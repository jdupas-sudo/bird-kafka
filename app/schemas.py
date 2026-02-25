from pydantic import BaseModel
from datetime import datetime
from typing import Optional


# --- Bird ---

class BirdCreate(BaseModel):
    common_name: str
    scientific_name: str
    family: str
    habitat: str
    wingspan_cm: Optional[float] = None
    weight_g: Optional[float] = None
    conservation_status: str = "Least Concern"
    description: Optional[str] = None
    region: str = "Unknown"


class BirdUpdate(BaseModel):
    common_name: Optional[str] = None
    scientific_name: Optional[str] = None
    family: Optional[str] = None
    habitat: Optional[str] = None
    wingspan_cm: Optional[float] = None
    weight_g: Optional[float] = None
    conservation_status: Optional[str] = None
    description: Optional[str] = None
    region: Optional[str] = None


class BirdOut(BaseModel):
    id: int
    common_name: str
    scientific_name: str
    family: str
    habitat: str
    wingspan_cm: Optional[float]
    weight_g: Optional[float]
    conservation_status: str
    description: Optional[str]
    region: str

    model_config = {"from_attributes": True}


# --- Sighting ---

class SightingCreate(BaseModel):
    location: str
    latitude: Optional[float] = None
    longitude: Optional[float] = None
    observer_name: Optional[str] = None
    notes: Optional[str] = None


class SightingOut(BaseModel):
    id: int
    bird_id: int
    location: str
    latitude: Optional[float]
    longitude: Optional[float]
    observed_at: datetime
    observer_name: Optional[str]
    notes: Optional[str]

    model_config = {"from_attributes": True}


# --- Diet ---

class DietItemOut(BaseModel):
    id: int
    bird_id: int
    food_type: str
    food_name: str
    percentage: Optional[float]
    season: Optional[str]

    model_config = {"from_attributes": True}


# --- Sighting Ingest (Kafka) ---

class SightingIngest(BaseModel):
    """Payload published to the sighting.ingest Kafka topic by external clients."""
    bird_id: int
    location: str
    latitude: Optional[float] = None
    longitude: Optional[float] = None
    observer_name: Optional[str] = None
    notes: Optional[str] = None


# --- Events ---

class EventOut(BaseModel):
    id: int
    event_type: str
    topic: str
    payload: str
    processed_at: datetime

    model_config = {"from_attributes": True}
