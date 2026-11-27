# Deploying to AWS Elastic Beanstalk

One Beanstalk environment runs one process: the Spring Boot jar with the React build packaged inside it.
The browser loads the page and calls `/api` on the same origin, so there is no CORS setup and no second
host to configure. PostgreSQL runs on Amazon RDS.

```
browser ──HTTP──> Beanstalk (nginx :80 ──> java -jar :5000) ──JDBC──> RDS PostgreSQL
```

## How configuration works

Every setting is an environment variable. [`.env.example`](../.env.example) lists all of them.

| Where | How to set them |
|---|---|
| Your machine | Copy `.env.example` to `.env` and uncomment what you need. The API reads it on start-up. |
| Elastic Beanstalk | **Environment properties** (console) or `eb setenv`. Beanstalk passes them to the app as real environment variables. |
| Elastic Beanstalk, secrets | Optional: keep them in Secrets Manager or Parameter Store and reference them as **environment secrets** (see the end of this page). |

If the same name is set in two places, a real environment variable beats `.env`, and a value set in the
console or with `eb setenv` beats `deploy/beanstalk/.ebextensions`. Don't put a `.env` file into the
Beanstalk bundle; the bundle is stored in S3 and anyone with access to that bucket can read it.

## 1. Create the database (RDS)

1. RDS → Create database → PostgreSQL 16, Free tier or `db.t4g.micro`.
2. Initial database name: `healthdekho`. Choose a master user and password.
3. Note the endpoint, for example `healthdekho.abc123.ap-south-1.rds.amazonaws.com`.
4. After the Beanstalk environment exists (step 3), edit the RDS security group. Allow inbound
   port 5432 from the environment's EC2 security group.

The schema is created automatically on the first start (`schema.sql` is idempotent). Don't use the
"Database" option inside the Beanstalk wizard. It ties the database to the environment, and
terminating the environment deletes the database.

## 2. Build the bundle

On Linux, macOS or WSL, with JDK 21, Maven and Node 20+:

```bash
./deploy/build-beanstalk.sh
# -> backend/target/healthdekho-beanstalk.zip  (healthdekho.jar + Procfile + .ebextensions)
```

The script does not run the tests. Run `mvn test` and `npm test` before you release.

## 3. Create the environment

Elastic Beanstalk → Create environment:

| Setting | Value |
|---|---|
| Environment tier | Web server |
| Platform | Java, branch **Corretto 21 running on 64bit Amazon Linux 2023** |
| Application code | Upload `healthdekho-beanstalk.zip` |
| Presets | Single instance (enough for a demo) |
| Instance type | `t3.small` or larger (the JVM is tight on `t3.micro`) |

`SPRING_PROFILES_ACTIVE=prod` and `PORT=5000` come from `deploy/beanstalk/.ebextensions/01-app.config`.
You don't need to type them.

## 4. Set the environment properties

The database URL, username and password are **not** environment properties: the `prod` profile reads
them from the Secrets Manager secret named by `SECRET_NAME` (`spring.config.import=aws-secretsmanager:...`).
The environment's instance profile needs `secretsmanager:GetSecretValue` on that secret.

Configuration → Updates, monitoring and logging → **Environment properties**:

| Name | Value | Required |
|---|---|---|
| `SECRET_NAME` | `prod/capstone` (already set by `.ebextensions`) | yes |
| `AWS_REGION` | `ap-south-1` (already set by `.ebextensions`) | yes |
| `JWT_SECRET` | 32+ random characters (`openssl rand -base64 48`) | yes |
| `AI_API_KEY` | your provider key | no (rule-based ranking without it) |
| `AI_BASE_URL`, `AI_MODEL`, `AI_COMPLETIONS_PATH` | see README, AI configuration | no |
| `SPRING_SQL_INIT_DATA_LOCATIONS` | `classpath:data.sql,classpath:demo-data.sql` | only for a demo |

The `prod` profile loads the schema only, so there are no users to sign in with. For a demo or review
environment, set `SPRING_SQL_INIT_DATA_LOCATIONS` as shown. It loads the same sample customers and
policies as in development (the README lists the logins). Leave it out for a real deployment.

Or from the terminal, with the EB CLI:

```bash
eb setenv DB_URL=jdbc:postgresql://<rds-endpoint>:5432/healthdekho \
          DB_USERNAME=... DB_PASSWORD=... JWT_SECRET="$(openssl rand -base64 48)"
```

Saving restarts the app. Open the environment URL and sign in.

## 5. Deploy a new version

Run `./deploy/build-beanstalk.sh` again and upload the new zip (Upload and deploy), or run `eb deploy`.
Environment properties stay as they are.

## Optional: secrets from Secrets Manager or Parameter Store

Beanstalk can read a value from Secrets Manager or SSM Parameter Store when an instance starts and
expose it as an environment variable. Your app sees an ordinary `DB_PASSWORD` or `JWT_SECRET`.

1. Store the value, for example the secret `healthdekho/jwt` or the SecureString parameter
   `/healthdekho/jwt-secret`.
2. Give the environment's instance profile (`aws-elasticbeanstalk-ec2-role` by default)
   `secretsmanager:GetSecretValue` or `ssm:GetParameter` on that ARN.
3. In Environment properties, add it as an **environment secret**: name `JWT_SECRET`, value the ARN.
   In configuration files the namespace is `aws:elasticbeanstalk:application:environmentsecrets`.

Values are read when instances start. After rotating a secret, restart the app servers to pick it up.

## When something goes wrong

| Symptom | Likely cause |
|---|---|
| 502 Bad Gateway | The app isn't listening on 5000, or it crashed on start. Check Logs → Request logs → `web.stdout.log`. |
| `JWT secret must be at least 32 characters` | `JWT_SECRET` is missing or too short. |
| `Connection refused` / timeout to the database | The RDS security group doesn't allow the Beanstalk instances, or `DB_URL` is wrong. |
| Sign-in fails with every account | Prod has no seed data; see `SPRING_SQL_INIT_DATA_LOCATIONS` above. |
