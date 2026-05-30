import numpy as np
import pandas as pd
from scipy import stats

def detect_anomalies(df):

    df["anomaly_types"] = [set() for _ in range(len(df))]

    for col in df.columns:

        if df[col].isnull().any():
            for idx in df[df[col].isnull()].index:
                df.at[idx, "anomaly_types"].add("Missing Values")

        if pd.api.types.is_numeric_dtype(df[col]):
            for idx in df[df[col] < 0].index:
                df.at[idx, "anomaly_types"].add("Invalid Negative Values")

    numeric = df.select_dtypes(include=[np.number]).fillna(0)

    if not numeric.empty:
        z = np.abs(stats.zscore(numeric))
        for idx in df[(z > 3).any(axis=1)].index:
            df.at[idx, "anomaly_types"].add("Z-Score Outliers")

    df["anomaly_types"] = df["anomaly_types"].apply(list)
    df["is_anomaly"] = df["anomaly_types"].apply(lambda x: len(x) > 0)

    return df