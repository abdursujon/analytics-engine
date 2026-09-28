# Dev Notes

## Convert CSV to Parquet (Ubuntu/Debian)

```bash
sudo apt install python3-venv -y          # one-time
python3 -m venv .venv                     # one-time, in project root
source .venv/bin/activate                 # every new terminal
pip install pandas pyarrow                # one-time per venv

python3 -c "import pandas as pd; pd.read_csv('input.csv').to_parquet('output.parquet', index=False)"

deactivate
```

## Inspect a Parquet file

```bash
snap install duckdb
duckdb -c "SELECT * FROM 'output.parquet' LIMIT 5"
```
