import json

events = []
total_cycles = 0

with open("mmio-event.jsonl") as f:
    events = [json.loads(line) for line in f]
events = list(filter(lambda x: x["event"] == "profile", events))

assert len(events) % 2 == 0

for i in range(len(events) // 2):
    assert events[2 * i]["value"] == 1
    assert events[2 * i + 1]["value"] == 0
    duration = events[2 * i + 1]["cycle"] - events[2 * i]["cycle"]
    total_cycles += duration

print(total_cycles)
