#!/usr/bin/env python3

import pymysql
import pymysql.cursors
import requests
import json
import os
import argparse
from typing import List, Tuple
from contextlib import contextmanager

# --- CLI Arguments ---
parser = argparse.ArgumentParser(description="Update table stats for MySQL clients.")
parser.add_argument("--client", help="Run for a specific client (e.g., 'MFM')")
parser.add_argument("--all", action="store_true", help="Run for all clients only if a stats process is running")
args = parser.parse_args()

# --- Environment Variables ---
SLACK_WEBHOOK_URL = os.getenv("SLACK_WEBHOOK_URL", "https://hooks.slack.com/services/...")
SLACK_CHANNEL = "#prod-stats-update-alert"

# --- Client Configuration ---
CLIENTS = [
    ("MFM", "mfm-prod.cluster-cpewz1juqrts.us-east-1.rds.amazonaws.com", "mfm_prod_admin", os.getenv("DB_PASS_MFM")),
    ("MFM Replica", "mfm-prod.cluster-ro-cpewz1juqrts.us-east-1.rds.amazonaws.com", "mfm_prod_admin", os.getenv("DB_PASS_MFM")),
    ("MFM Correspondent", "mfmcorr-prod.cluster-cphgt2qz1ifr.us-east-1.rds.amazonaws.com", "mfmcorr_prod_adm", os.getenv("DB_PASS_MFMCORR")),
    # Add more clients as needed
]

@contextmanager
def get_connection(host: str, user: str, password: str):
    connection = pymysql.connect(
        host=host,
        user=user,
        password=password,
        charset='utf8mb4',
        autocommit=True,
        cursorclass=pymysql.cursors.DictCursor
    )
    try:
        yield connection
    finally:
        connection.close()

def has_statistics_process(cursor) -> bool:
    cursor.execute("SELECT COUNT(*) as count FROM information_schema.processlist WHERE STATE LIKE '%Stat%';")
    return cursor.fetchone()['count'] > 0

def get_statistics_query_info(cursor) -> str:
    cursor.execute("SELECT INFO FROM information_schema.processlist WHERE STATE LIKE '%Stat%' ORDER BY TIME DESC LIMIT 1;")
    result = cursor.fetchone()
    return result['INFO'] if result else "No query info found."

def update_table_stats(cursor, client: str, schema: str, table: str) -> bool:
    try:
        cursor.execute(f"ANALYZE NO_WRITE_TO_BINLOG TABLE `{schema}`.`{table}`;")
        print(f"Stats updated for {schema}.{table}")
        return True
    except pymysql.Error as e:
        print(f"Failed to analyze {schema}.{table} for client {client}: {e}")
        return False

def process_client(client_name, host, user, password, force_run=False) -> Tuple[str, str]:
    print(f"Connecting to client: {client_name}, host: {host}")
    try:
        with get_connection(host, user, password) as conn:
            with conn.cursor() as cursor:
                if not force_run and not has_statistics_process(cursor):
                    print(f"No active statistics process found for client: {client_name}. Skipping.")
                    return None

                print(f"Proceeding with stats update for {client_name}")
                query_info = get_statistics_query_info(cursor)

                cursor.execute("""
                    SELECT TABLE_NAME, TABLE_SCHEMA
                    FROM information_schema.tables
                    WHERE TABLE_SCHEMA NOT IN ('information_schema', 'performance_schema', 'mysql', 'sys')
                    AND TABLE_TYPE = 'BASE TABLE';
                """)
                tables = cursor.fetchall()

                updated = False
                for table in tables:
                    schema = table['TABLE_SCHEMA']
                    table_name = table['TABLE_NAME']
                    if update_table_stats(cursor, client_name, schema, table_name):
                        updated = True

                if updated:
                    return (client_name, query_info)
    except Exception as e:
        print(f"Error processing {client_name}: {e}")
    return None

def main():
    results: List[Tuple[str, str]] = []

    if args.all:
        print("Running in scheduled ALL mode. Will check for active stats process before proceeding.")
        for client_name, host, user, password in CLIENTS:
            result = process_client(client_name, host, user, password, force_run=False)
            if result:
                results.append(result)

    elif args.client:
        selected = args.client.strip().lower()
        print(f"Running in on-demand mode for client: {selected}")
        for client_name, host, user, password in CLIENTS:
            if client_name.strip().lower() == selected:
                result = process_client(client_name, host, user, password, force_run=True)
                if result:
                    results.append(result)
                break
        else:
            print(f"No match found for client: {args.client}")
    else:
        print("❌ ERROR: You must provide either --client or --all")
        return

    # Slack Notification
    if results:
        message = {
            "channel": SLACK_CHANNEL,
            "username": "StatsBot",
            "icon_emoji": ":robot_face:",
            "blocks": []
        }
        for client, info in results:
            message["blocks"].extend([
                {"type": "section", "text": {"type": "mrkdwn", "text": f"*Client*: {client}"}},
                {"type": "section", "text": {"type": "mrkdwn", "text": f"*Query Info*: {info}"}},
                {"type": "divider"}
            ])
    else:
        message = {
            "channel": SLACK_CHANNEL,
            "username": "StatsBot",
            "text": "No statistics updates were performed.",
            "icon_emoji": ":robot_face:"
        }

    try:
        response = requests.post(SLACK_WEBHOOK_URL, headers={"Content-Type": "application/json"}, json=message)
        response.raise_for_status()
        print("Slack message sent successfully.")
    except requests.RequestException as e:
        print(f"Failed to send Slack message: {e}")

if __name__ == "__main__":
    main()
