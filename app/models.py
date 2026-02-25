from sqlalchemy import Column, Integer, String, Float, Text, ForeignKey, DateTime, create_engine
from sqlalchemy.orm import declarative_base, relationship, sessionmaker
import datetime

Base = declarative_base()


class Bird(Base):
    __tablename__ = "birds"

    id = Column(Integer, primary_key=True, index=True)
    common_name = Column(String(100), nullable=False, index=True)
    scientific_name = Column(String(150), nullable=False)
    family = Column(String(100), nullable=False)
    habitat = Column(String(200), nullable=False)
    wingspan_cm = Column(Float)
    weight_g = Column(Float)
    conservation_status = Column(String(50), nullable=False)
    description = Column(Text)
    region = Column(String(100), nullable=False)

    sightings = relationship("Sighting", back_populates="bird", cascade="all, delete-orphan")
    diet_items = relationship("DietItem", back_populates="bird", cascade="all, delete-orphan")


class Sighting(Base):
    __tablename__ = "sightings"

    id = Column(Integer, primary_key=True, index=True)
    bird_id = Column(Integer, ForeignKey("birds.id"), nullable=False)
    location = Column(String(200), nullable=False)
    latitude = Column(Float)
    longitude = Column(Float)
    observed_at = Column(DateTime, default=datetime.datetime.utcnow)
    observer_name = Column(String(100))
    notes = Column(Text)

    bird = relationship("Bird", back_populates="sightings")


class DietItem(Base):
    __tablename__ = "diet_items"

    id = Column(Integer, primary_key=True, index=True)
    bird_id = Column(Integer, ForeignKey("birds.id"), nullable=False)
    food_type = Column(String(50), nullable=False)  # e.g. "insect", "seed", "fish"
    food_name = Column(String(100), nullable=False)
    percentage = Column(Float)  # percentage of diet
    season = Column(String(20))  # "spring", "summer", "fall", "winter", "year-round"

    bird = relationship("Bird", back_populates="diet_items")


class BirdEvent(Base):
    __tablename__ = "bird_events"

    id = Column(Integer, primary_key=True, index=True)
    event_type = Column(String(50), nullable=False)
    topic = Column(String(100), nullable=False)
    payload = Column(Text, nullable=False)
    processed_at = Column(DateTime, default=datetime.datetime.utcnow)


DATABASE_URL = "sqlite:///./birds.db"

engine = create_engine(DATABASE_URL, connect_args={"check_same_thread": False})
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)


def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
