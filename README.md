# 🔧 MySQL Table Statistics Updater

A Python-based automation tool that connects to multiple Amazon RDS MySQL databases, updates table statistics across all non-system schemas, and sends a summary alert to Slack.

---

## 📌 Features

- ✅ Connects securely to multiple RDS instances
- ✅ Automatically runs `ANALYZE NO_WRITE_TO_BINLOG` on all tables
- ✅ Skips system schemas (`information_schema`, `mysql`, etc.)
- ✅ Sends Slack notification on success/failure
- ✅ Designed for automation via **Jenkins**

---

## 🚀 Usage

### 🔧 Requirements

- Python 3.7+
- `pymysql`
- `requests`

Install dependencies:

```bash
pip install -r requirements.txt

