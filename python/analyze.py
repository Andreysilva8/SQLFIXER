import sys
import json
import pandas as pd
from anomaly_detection import detect_anomalies

def main():
    try:
        data = json.loads(sys.stdin.read())
        df = pd.DataFrame(data)

        result = detect_anomalies(df)
        anomalies = result[result["is_anomaly"] == True]

        print(anomalies.to_json(orient="records"))

    except Exception as e:
        print(json.dumps({"erro": str(e)}))

if __name__ == "__main__":
    main()