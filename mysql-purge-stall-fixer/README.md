# InnoDB Purge Stall Fixer

🔧 A Python script to detect and automatically resolve stalled InnoDB purge operations on MySQL (including Amazon RDS).

## Features

- Monitors `SHOW ENGINE INNODB STATUS` for purge progress
- Detects when purge is stalled
- Identifies and kills long-running or blocking transactions
- Logs thread details and query text before termination
- Works with RDS-safe connections (avoids killing `rdsadmin`/replication)

## Requirements

- Python 3.7+
- `pymysql`

## Installation

```bash
pip install pymysql
