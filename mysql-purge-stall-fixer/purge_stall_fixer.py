import pymysql
import time
import json
import re
from datetime import datetime
from pathlib import Path

# Regex patterns to extract metrics
trx_id_pattern = re.compile(r'Trx id counter (\d+)')
purge_done_pattern = re.compile(r"Purge done for trx's n:o < (\d+)")
history_list_pattern = re.compile(r'History list length (\d+)')

# Global tracking
last_trx_id = None
last_purge_done = None
stalled_count = 0

# Load DB config
CONFIG_PATH = Path(__file__).parent / "config.json"
with open(CONFIG_PATH) as f:
    config = json.load(f)

# Log helper
LOG_PATH = Path(__file__).parent / "purge_fixer.log"
def log(message):
    now = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    log_entry = f"[{now}] {message}"
    print(log_entry)
    with open(LOG_PATH, "a", encoding="utf-8") as log_file:
        log_file.write(log_entry + "\n")

def get_innodb_status():
    try:
        conn = pymysql.connect(**config)
        with conn.cursor() as cursor:
            cursor.execute("SHOW ENGINE INNODB STATUS")
            row = cursor.fetchone()
            return row[2] if row else None
    except Exception as e:
        log(f"[ERROR] Could not fetch InnoDB status: {e}")
        return None
    finally:
        conn.close()

def parse_status(text):
    trx_id = purge_done = history_list = None
    for line in text.split('\n'):
        if not trx_id and (m := trx_id_pattern.search(line)):
            trx_id = int(m.group(1))
        elif not purge_done and (m := purge_done_pattern.search(line)):
            purge_done = int(m.group(1))
        elif not history_list and (m := history_list_pattern.search(line)):
            history_list = int(m.group(1))
    return trx_id, purge_done, history_list

def kill_blocking_transactions():
    log("\n🔍 Looking for active InnoDB transactions to kill...")
    try:
        conn = pymysql.connect(**config)
        with conn.cursor(pymysql.cursors.DictCursor) as cursor:
            cursor.execute("""
                SELECT t.trx_id, t.trx_started, t.trx_mysql_thread_id as thread_id,
                       p.USER, p.HOST, p.TIME, p.INFO
                FROM information_schema.innodb_trx t
                JOIN information_schema.processlist p
                  ON t.trx_mysql_thread_id = p.ID
                WHERE p.USER NOT IN ('rdsadmin', 'rdsrepladmin')
            """)
            for row in cursor.fetchall():
                log(f"  🚫 KILLING trx_id={row['trx_id']}, thread_id={row['thread_id']}, started={row['trx_started']}")
                if row['INFO']:
                    log(f"     Query: {row['INFO'][:500]}")
                try:
                    cursor.execute(f"KILL {row['thread_id']}")
                    log(f"     ✅ Successfully killed thread {row['thread_id']}")
                except Exception as kill_err:
                    log(f"     ❌ Failed to kill thread {row['thread_id']}: {kill_err}")
    except Exception as e:
        log(f"[ERROR] Failed during transaction kill phase: {e}")
    finally:
        conn.close()

def monitor_loop():
    global last_trx_id, last_purge_done, stalled_count

    while True:
        log("\n--- InnoDB Purge Monitor ---")
        status = get_innodb_status()

        if status:
            trx_id, purge_done, hist_len = parse_status(status)
            if trx_id and purge_done:
                delta_trx = trx_id - last_trx_id if last_trx_id else 0
                delta_purge = purge_done - last_purge_done if last_purge_done else 0

                log(f"Trx ID: {trx_id} | Purge Done: {purge_done} | History List Length: {hist_len}")
                log(f"  Δ Trx ID: {delta_trx} | Δ Purged: {delta_purge}")

                if delta_purge == 0:
                    stalled_count += 1
                    log(f"  ⚠️  Purge may be stalled! ({stalled_count}/3)")
                else:
                    stalled_count = 0

                last_trx_id, last_purge_done = trx_id, purge_done

                if stalled_count >= 3:
                    log("  🔔 ALERT: Purge stalled too long. Attempting to kill blocking transactions.")
                    kill_blocking_transactions()
                    stalled_count = 0
            else:
                log("Could not parse required metrics from InnoDB status.")
        else:
            log("Failed to retrieve InnoDB status.")

        time.sleep(300)  # 5 minutes

if __name__ == "__main__":
    monitor_loop()
