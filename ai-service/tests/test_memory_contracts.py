from app.api.memory_contracts import (
    MEMORY_TYPES,
    MEMORY_SAVE_PLAN_SCHEMA,
    ORGANIZED_DRAFT_SCHEMA,
    PERSONA_MATERIAL_DRAFT_SCHEMA,
    WEEKLY_REPORT_SCHEMA,
)


STRICT_SCHEMAS = [
    MEMORY_SAVE_PLAN_SCHEMA,
    ORGANIZED_DRAFT_SCHEMA,
    PERSONA_MATERIAL_DRAFT_SCHEMA,
    WEEKLY_REPORT_SCHEMA,
]


def _walk_object_schemas(schema: dict):
    if schema.get("type") == "object":
        yield schema
        for child in schema.get("properties", {}).values():
            yield from _walk_object_schemas(child)
    if schema.get("type") == "array":
        yield from _walk_object_schemas(schema.get("items", {}))


def _inner_schema(response_format: dict) -> dict:
    return response_format["json_schema"]["schema"]


def test_memory_response_formats_are_strict_json_schema_contracts():
    for response_format in STRICT_SCHEMAS:
        assert response_format["type"] == "json_schema"
        assert response_format["json_schema"]["strict"] is True
        for object_schema in _walk_object_schemas(_inner_schema(response_format)):
            assert object_schema["additionalProperties"] is False
            assert set(object_schema["required"]) == set(object_schema["properties"])


def test_memory_save_plan_schema_bounds_values_before_sanitizer():
    properties = _inner_schema(MEMORY_SAVE_PLAN_SCHEMA)["properties"]

    assert properties["memory_library"]["enum"] == ["PERSONAL", "FAMILY"]
    assert "SELECTED_FAMILIES_VISIBLE" in properties["visibility"]["enum"]
    assert properties["memory_type"]["enum"] == MEMORY_TYPES
    assert properties["importance"] == {"type": "integer", "minimum": 1, "maximum": 5}
    assert properties["tags"]["maxItems"] == 6
    assert properties["tags"]["items"]["maxLength"] == 18


def test_persona_material_schema_bounds_nested_material_cards():
    properties = _inner_schema(PERSONA_MATERIAL_DRAFT_SCHEMA)["properties"]
    profile = properties["profile"]
    materials = properties["materials"]
    material_item = materials["items"]

    assert profile["additionalProperties"] is False
    assert profile["properties"]["description"]["maxLength"] == 500
    assert materials["maxItems"] == 5
    assert material_item["additionalProperties"] is False
    assert material_item["properties"]["content"]["maxLength"] == 600
    assert material_item["properties"]["tags"]["maxItems"] == 6
