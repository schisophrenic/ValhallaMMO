#!/usr/bin/env python3
"""Static contract checks for the independent Apothic Enchanting stats.

This does not compile or simulate Paper/Purpur. It verifies consistency
between the player's overhaul planner, the bundled legacy YAML, localization,
profile reward registration and stat source names.
"""
from __future__ import annotations

import json
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "core/src/main/java/me/athlaeos/valhallammo"
RESOURCES = ROOT / "core/src/main/resources"
PLANNER = ROOT / "tools/skill-tree"


def read_java(path: str) -> str:
    return (JAVA / path).read_text(encoding="utf-8")


def main() -> None:
    data = json.loads((PLANNER / "skill-tree-layout.json").read_text(encoding="utf-8"))
    text = (PLANNER / "Skill_Tree_Planner.html").read_text(encoding="utf-8")
    marker = "const DATA="
    assert marker in text, "The HTML editor has no embedded data"
    embedded, _ = json.JSONDecoder().raw_decode(text.split(marker, 1)[1].lstrip())
    assert data == embedded, "Planner HTML and JSON are out of sync"

    tree = next(s for s in data if s.get("id") == "enchanting")
    nodes = {p["id"]: p for p in tree["perks"]}
    positions: set[tuple[int, int]] = set()
    for p in tree["perks"]:
        if p.get("hidden"):
            continue
        position = (p["x"], p["y"])
        assert position not in positions, f"Overlapping visible enchant perks at {position}"
        positions.add(position)

    progression = yaml.safe_load((RESOURCES / "skills/enchanting_progression.yml").read_text(encoding="utf-8"))
    perks = progression["perks"]
    lang = json.loads((RESOURCES / "languages/en-us.json").read_text(encoding="utf-8"))
    texts, lists = lang["stringTranslations"], lang["stringListTranslations"]
    profile = read_java("playerstats/profiles/implementations/EnchantingProfile.java")
    collector = read_java("playerstats/AccumulativeStatManager.java")
    attributes = read_java("item/ItemAttributesRegistry.java")

    previous = "enchanting_perk_b1"
    for name, level, amount, position in (
        ("eterna", 35, 8, (5, 10)),
        ("quanta", 55, 12, (7, 10)),
        ("arcana", 75, 15, (9, 10)),
    ):
        node_id = f"enchanting_perk_apothic_{name}"
        reward = f"enchanting_apothic{name}_add"
        stat = f"ENCHANTING_{name.upper()}"
        property_name = f"apothic{name.capitalize()}"
        node, yaml_node = nodes[node_id], perks[node_id]

        assert node["all"] == [previous], node_id
        assert node["lv"] == level, node_id
        assert node["cost"] == 1, node_id
        assert node["cl"] is True, node_id
        assert (node["x"], node["y"]) == position, node_id
        assert not node["hidden"] and node["ng"] == 0, node_id
        assert yaml_node["requireperk_all"] == [previous], node_id
        assert yaml_node["required_lv"] == level, node_id
        assert yaml_node["perk_rewards"][reward] == amount, node_id
        assert yaml_node["cost"] == 1, node_id

        assert texts[node_id + "_name"] and texts[node_id + "_description"]
        assert lists[node_id + "_messages"]
        assert texts["attribute_enchanting_" + name]
        assert f'floatStat("{property_name}"' in profile
        assert f'register("{stat}"' in collector
        assert f'new ProfileStatSource(EnchantingProfile.class, "{property_name}")' in collector
        assert f'new AttributeSource("{stat}")' in collector
        assert f'new AttributeDisplayWrapper("{stat}"' in attributes
        previous = node_id

    assert 'register("ENCHANTING_QUALITY"' in collector
    assert "enchanting_perk_ng1" in perks
    assert "enchanting_perk_ng5" in nodes
    print("PASS: 3 independent enchanting stats, 3 perk rewards,")
    print("      connected 53-node redesigned Enchanting planner and HTML sync.")
    print("NOTE: Source compilation and Purpur runtime are NOT tested by this validator.")


if __name__ == "__main__":
    main()
