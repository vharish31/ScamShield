"""
Tests for Trusted Contacts and Analysis History APIs
"""

from starlette.testclient import TestClient


def test_create_and_list_trusted_contacts(client: TestClient, auth_headers):
    """Verify adding a contact and retrieving the contact list for current user."""
    payload = {
        "name": "Jane Doe",
        "phone_number": "+1-555-0199",
        "email": "jane@example.com",
        "relationship": "Sister",
        "notify_on_high_risk": True,
    }

    create_resp = client.post("/api/contacts", json=payload, headers=auth_headers)
    assert create_resp.status_code == 201
    contact = create_resp.json()
    assert contact["name"] == "Jane Doe"
    assert contact["relationship"] == "Sister"
    assert contact["user_id"] == "dev_user_testuser123"
    contact_id = contact["id"]

    # List contacts
    list_resp = client.get("/api/contacts", headers=auth_headers)
    assert list_resp.status_code == 200
    list_data = list_resp.json()
    assert list_data["total"] >= 1
    assert any(c["id"] == contact_id for c in list_data["contacts"])

    # Delete contact
    del_resp = client.delete(f"/api/contacts/{contact_id}", headers=auth_headers)
    assert del_resp.status_code == 200
    assert del_resp.json()["status"] == "deleted"


def test_history_lifecycle(client: TestClient, auth_headers):
    """Verify that an analysis performed by user appears in history and can be retrieved and deleted."""
    # Run an analysis
    analysis_resp = client.post(
        "/api/analyze/text",
        json={"text": "Transfer money immediately. Police are at your door."},
        headers=auth_headers,
    )
    assert analysis_resp.status_code == 200
    analysis_id = analysis_resp.json()["analysis_id"]

    # Check history list
    hist_resp = client.get("/api/history", headers=auth_headers)
    assert hist_resp.status_code == 200
    hist_data = hist_resp.json()
    assert hist_data["total"] >= 1
    assert any(h["analysis_id"] == analysis_id for h in hist_data["items"])

    # Get single history detail
    detail_resp = client.get(f"/api/history/{analysis_id}", headers=auth_headers)
    assert detail_resp.status_code == 200
    assert detail_resp.json()["analysis_id"] == analysis_id

    # Delete history record
    del_resp = client.delete(f"/api/history/{analysis_id}", headers=auth_headers)
    assert del_resp.status_code == 200
    assert del_resp.json()["status"] == "deleted"

