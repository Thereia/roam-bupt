import csv, subprocess, io, os

MYSQL = r"E:\MySQL\MySQL Server 8.0\bin\mysql.exe"
OUT = r"E:\code\roam-bupt\docs\data"
NULL = "\\N"


def dump(sql):
    p = subprocess.run(
        [MYSQL, "-uroot", "-p123456", "-h127.0.0.1", "--default-character-set=utf8mb4",
         "-B", "-N", "--raw", "-e", sql],
        capture_output=True)
    if p.returncode != 0:
        raise SystemExit(p.stderr.decode("utf-8", "ignore"))
    rows = []
    for line in p.stdout.decode("utf-8").splitlines():
        if not line:
            continue
        parts = line.split("\t")
        rows.append(["" if v == NULL else v for v in parts])
    return rows


TASKS = {
    "route_node.csv": (
        """SELECT node_id,node_name,node_type,building_id,floor,x_ratio,y_ratio,poi_category,enabled
           FROM travel_system.route_node ORDER BY node_id""",
        ["node_id", "node_name", "node_type", "building_id", "floor",
         "x_ratio", "y_ratio", "poi_category", "enabled"]),
    "route_edge.csv": (
        """SELECT edge_id,from_node_id,to_node_id,distance_meters,time_seconds,edge_type,
                  bike_allowed,congestion_factor,enabled
           FROM travel_system.route_edge ORDER BY edge_id""",
        ["edge_id", "from_node_id", "to_node_id", "distance_meters", "time_seconds",
         "edge_type", "bike_allowed", "congestion_factor", "enabled"]),
    "topic_place.csv": (
        """SELECT poi_id,poi_name,poi_category,node_id,heat_score,rating_score,enabled
           FROM travel_system.campus_poi ORDER BY poi_id""",
        ["source_poi_id", "name", "category", "node_id", "heat", "rating", "enabled"]),
}

for name, (sql, cols) in TASKS.items():
    rows = dump(sql)
    path = os.path.join(OUT, name)
    with io.open(path, "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f, lineterminator="\n")
        w.writerow(cols)
        w.writerows(rows)
    print(name, len(rows))
