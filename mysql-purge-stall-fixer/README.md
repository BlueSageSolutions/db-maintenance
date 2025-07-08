# 🧹 MySQL InnoDB Purge Stall Fixer

A Python script that monitors the InnoDB history list length and **automatically kills blocking transactions** when purge stalls occur. Built for AWS RDS environments where manual intervention is time-sensitive.

---

## 📌 Features

- ✅ Monitors `SHOW ENGINE INNODB STATUS` every 5 minutes
- ⚠️ Detects when `Purge Done` transaction ID stops progressing
- 🚫 Kills offending transactions that prevent purge
- 🧠 Prints real-time thread info, SQL queries, and kill confirmations
- 💾 Logs activity to a `.log` file for auditing

---

## 📂 File Structure

```

mysql-purge-stall-fixer/
├── purge\_stall\_fixer.py        # Main script
├── config.sample.json          # Sample config (rename to config.json)
├── .gitignore
└── README.md

````

---

## ⚙️ Configuration

Create a file called `config.json` in the root directory with the following content:

```json
{
  "host": "your-rds-endpoint",
  "user": "your-user",
  "password": "your-password",
  "database": "information_schema"
}
````

> ⚠️ Never commit credentials to GitHub! This file is ignored in `.gitignore`.

---

## 🚀 Usage

1. Clone the repository:

   ```bash
   git clone https://github.com/BlueSageSolutions/db-maintenance.git
   cd mysql-purge-stall-fixer
   ```

2. Set up a Python virtual environment:

   ```bash
   python -m venv .venv
   source .venv/bin/activate  # On Windows: .venv\Scripts\activate
   pip install pymysql
   ```

3. Run the script:

   ```bash
   python purge_stall_fixer.py
   ```

---

## 🧠 How It Works

* Parses `SHOW ENGINE INNODB STATUS` for:

  * `Trx id counter`
  * `Purge done for trx's n:o <`
  * `History list length`
* Tracks deltas between checks.
* After **3 failed purge checks**, it queries active transactions from `information_schema.innodb_trx` and issues `KILL <thread_id>` for eligible threads.

---

## 🛑 Safeguards

* Skips killing `system user` or `rdsrepladmin`
* Ignores empty queries
* Thread details are printed before kill
* Kill logs are timestamped

---

## 🧪 Tested On

* ✅ AWS RDS MySQL 5.7 / 8.0
* ✅ Python 3.8+
* ✅ pymysql 1.x

---

## 📜 License

MIT License © \[Your Name]

---

## 🤝 Contributions

PRs are welcome! If you’d like to improve detection logic, add Slack/email alerts, or integrate with CloudWatch, feel free to fork and submit.

---

## 📸 Screenshot

![purge-monitor-output](./screenshot.png)

---


