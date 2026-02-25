import datetime
from app.models import Base, engine, SessionLocal, Bird, Sighting, DietItem

BIRDS = [
    {
        "common_name": "Bald Eagle",
        "scientific_name": "Haliaeetus leucocephalus",
        "family": "Accipitridae",
        "habitat": "Near large bodies of open water",
        "wingspan_cm": 200.0,
        "weight_g": 4500.0,
        "conservation_status": "Least Concern",
        "description": "Large raptor with white head and tail, dark brown body. National bird of the United States.",
        "region": "North America",
        "diet": [
            {"food_type": "fish", "food_name": "Salmon", "percentage": 50, "season": "year-round"},
            {"food_type": "fish", "food_name": "Trout", "percentage": 20, "season": "year-round"},
            {"food_type": "mammal", "food_name": "Rabbit", "percentage": 15, "season": "winter"},
            {"food_type": "bird", "food_name": "Waterfowl", "percentage": 15, "season": "fall"},
        ],
        "sightings": [
            {"location": "Yellowstone National Park, WY", "latitude": 44.59, "longitude": -110.55, "observer_name": "Alice Chen"},
            {"location": "Chesapeake Bay, MD", "latitude": 38.98, "longitude": -76.49, "observer_name": "Bob Garcia"},
        ],
    },
    {
        "common_name": "European Robin",
        "scientific_name": "Erithacus rubecula",
        "family": "Muscicapidae",
        "habitat": "Woodlands, gardens, hedgerows",
        "wingspan_cm": 22.0,
        "weight_g": 18.0,
        "conservation_status": "Least Concern",
        "description": "Small passerine with distinctive orange-red breast. Common throughout Europe.",
        "region": "Europe",
        "diet": [
            {"food_type": "insect", "food_name": "Beetles", "percentage": 40, "season": "spring"},
            {"food_type": "insect", "food_name": "Caterpillars", "percentage": 25, "season": "summer"},
            {"food_type": "fruit", "food_name": "Berries", "percentage": 25, "season": "fall"},
            {"food_type": "seed", "food_name": "Sunflower seeds", "percentage": 10, "season": "winter"},
        ],
        "sightings": [
            {"location": "Hyde Park, London, UK", "latitude": 51.507, "longitude": -0.164, "observer_name": "Clara Bell"},
        ],
    },
    {
        "common_name": "Emperor Penguin",
        "scientific_name": "Aptenodytes forsteri",
        "family": "Spheniscidae",
        "habitat": "Antarctic ice and surrounding oceans",
        "wingspan_cm": 76.0,
        "weight_g": 23000.0,
        "conservation_status": "Near Threatened",
        "description": "Tallest and heaviest of all living penguin species. Breeds during Antarctic winter.",
        "region": "Antarctica",
        "diet": [
            {"food_type": "fish", "food_name": "Antarctic silverfish", "percentage": 50, "season": "year-round"},
            {"food_type": "crustacean", "food_name": "Krill", "percentage": 35, "season": "year-round"},
            {"food_type": "mollusk", "food_name": "Squid", "percentage": 15, "season": "summer"},
        ],
        "sightings": [
            {"location": "Ross Ice Shelf, Antarctica", "latitude": -81.5, "longitude": 175.0, "observer_name": "Dr. Nakamura"},
        ],
    },
    {
        "common_name": "Peregrine Falcon",
        "scientific_name": "Falco peregrinus",
        "family": "Falconidae",
        "habitat": "Cliffs, tall buildings, open landscapes",
        "wingspan_cm": 104.0,
        "weight_g": 900.0,
        "conservation_status": "Least Concern",
        "description": "Fastest animal on Earth, reaching speeds over 300 km/h in a dive. Cosmopolitan raptor.",
        "region": "Worldwide",
        "diet": [
            {"food_type": "bird", "food_name": "Pigeons", "percentage": 60, "season": "year-round"},
            {"food_type": "bird", "food_name": "Starlings", "percentage": 20, "season": "year-round"},
            {"food_type": "mammal", "food_name": "Bats", "percentage": 10, "season": "summer"},
            {"food_type": "insect", "food_name": "Dragonflies", "percentage": 10, "season": "summer"},
        ],
        "sightings": [
            {"location": "Brooklyn Bridge, New York, NY", "latitude": 40.706, "longitude": -73.997, "observer_name": "James W."},
            {"location": "White Cliffs of Dover, UK", "latitude": 51.13, "longitude": 1.33, "observer_name": "Emily Stone"},
        ],
    },
    {
        "common_name": "Atlantic Puffin",
        "scientific_name": "Fratercula arctica",
        "family": "Alcidae",
        "habitat": "Coastal cliffs and offshore islands",
        "wingspan_cm": 53.0,
        "weight_g": 380.0,
        "conservation_status": "Vulnerable",
        "description": "Distinctive seabird with colorful beak. Excellent swimmers, using wings to fly underwater.",
        "region": "North Atlantic",
        "diet": [
            {"food_type": "fish", "food_name": "Sand eels", "percentage": 60, "season": "year-round"},
            {"food_type": "fish", "food_name": "Herring", "percentage": 25, "season": "summer"},
            {"food_type": "crustacean", "food_name": "Shrimp", "percentage": 15, "season": "winter"},
        ],
        "sightings": [
            {"location": "Skellig Michael, Ireland", "latitude": 51.77, "longitude": -10.54, "observer_name": "Paddy O'Brien"},
        ],
    },
    {
        "common_name": "Superb Lyrebird",
        "scientific_name": "Menura novaehollandiae",
        "family": "Menuridae",
        "habitat": "Temperate rainforest and wet sclerophyll forest",
        "wingspan_cm": 78.0,
        "weight_g": 950.0,
        "conservation_status": "Least Concern",
        "description": "Famous for extraordinary ability to mimic natural and artificial sounds. Males have elaborate tail feathers.",
        "region": "Australia",
        "diet": [
            {"food_type": "insect", "food_name": "Beetles", "percentage": 35, "season": "year-round"},
            {"food_type": "insect", "food_name": "Spiders", "percentage": 25, "season": "year-round"},
            {"food_type": "insect", "food_name": "Earthworms", "percentage": 25, "season": "year-round"},
            {"food_type": "seed", "food_name": "Seeds", "percentage": 15, "season": "fall"},
        ],
        "sightings": [
            {"location": "Blue Mountains, NSW, Australia", "latitude": -33.72, "longitude": 150.31, "observer_name": "Sophie Liu"},
        ],
    },
    {
        "common_name": "Resplendent Quetzal",
        "scientific_name": "Pharomachrus mocinno",
        "family": "Trogonidae",
        "habitat": "Cloud forests",
        "wingspan_cm": 45.0,
        "weight_g": 210.0,
        "conservation_status": "Near Threatened",
        "description": "Strikingly colored bird sacred to ancient Maya and Aztec peoples. Males have long tail coverts.",
        "region": "Central America",
        "diet": [
            {"food_type": "fruit", "food_name": "Wild avocado", "percentage": 50, "season": "year-round"},
            {"food_type": "insect", "food_name": "Wasps", "percentage": 20, "season": "summer"},
            {"food_type": "insect", "food_name": "Ants", "percentage": 15, "season": "year-round"},
            {"food_type": "amphibian", "food_name": "Tree frogs", "percentage": 15, "season": "spring"},
        ],
        "sightings": [
            {"location": "Monteverde Cloud Forest, Costa Rica", "latitude": 10.3, "longitude": -84.82, "observer_name": "Maria Gonzalez"},
        ],
    },
    {
        "common_name": "Japanese Crane",
        "scientific_name": "Grus japonensis",
        "family": "Gruidae",
        "habitat": "Marshes, riverbanks, rice paddies",
        "wingspan_cm": 250.0,
        "weight_g": 9000.0,
        "conservation_status": "Endangered",
        "description": "One of the rarest cranes. Symbol of luck, longevity, and fidelity in East Asia.",
        "region": "East Asia",
        "diet": [
            {"food_type": "fish", "food_name": "Mudfish", "percentage": 30, "season": "summer"},
            {"food_type": "amphibian", "food_name": "Frogs", "percentage": 25, "season": "spring"},
            {"food_type": "insect", "food_name": "Dragonfly larvae", "percentage": 20, "season": "summer"},
            {"food_type": "plant", "food_name": "Rice grain", "percentage": 25, "season": "fall"},
        ],
        "sightings": [
            {"location": "Kushiro Marshland, Hokkaido, Japan", "latitude": 43.08, "longitude": 144.37, "observer_name": "Yuki Tanaka"},
        ],
    },
    {
        "common_name": "Scarlet Macaw",
        "scientific_name": "Ara macao",
        "family": "Psittacidae",
        "habitat": "Tropical rainforest canopy",
        "wingspan_cm": 100.0,
        "weight_g": 1000.0,
        "conservation_status": "Least Concern",
        "description": "Large, vibrantly colored parrot. Highly intelligent and social, can live over 50 years.",
        "region": "Central and South America",
        "diet": [
            {"food_type": "seed", "food_name": "Palm nuts", "percentage": 40, "season": "year-round"},
            {"food_type": "fruit", "food_name": "Figs", "percentage": 30, "season": "year-round"},
            {"food_type": "seed", "food_name": "Sunflower seeds", "percentage": 15, "season": "year-round"},
            {"food_type": "insect", "food_name": "Beetle larvae", "percentage": 15, "season": "spring"},
        ],
        "sightings": [
            {"location": "Corcovado National Park, Costa Rica", "latitude": 8.53, "longitude": -83.58, "observer_name": "Diego Ramos"},
            {"location": "Tambopata Reserve, Peru", "latitude": -12.83, "longitude": -69.29, "observer_name": "Ana Vargas"},
        ],
    },
    {
        "common_name": "Snowy Owl",
        "scientific_name": "Bubo scandiacus",
        "family": "Strigidae",
        "habitat": "Arctic tundra, open grasslands",
        "wingspan_cm": 150.0,
        "weight_g": 2000.0,
        "conservation_status": "Vulnerable",
        "description": "Large white owl of Arctic regions. One of few owl species active during the day.",
        "region": "Arctic",
        "diet": [
            {"food_type": "mammal", "food_name": "Lemmings", "percentage": 60, "season": "year-round"},
            {"food_type": "mammal", "food_name": "Voles", "percentage": 20, "season": "winter"},
            {"food_type": "bird", "food_name": "Ptarmigan", "percentage": 10, "season": "winter"},
            {"food_type": "fish", "food_name": "Arctic char", "percentage": 10, "season": "summer"},
        ],
        "sightings": [
            {"location": "Barrow, Alaska", "latitude": 71.29, "longitude": -156.79, "observer_name": "Mike Frost"},
        ],
    },
    {
        "common_name": "Greater Flamingo",
        "scientific_name": "Phoenicopterus roseus",
        "family": "Phoenicopteridae",
        "habitat": "Alkaline and saline lakes, estuaries",
        "wingspan_cm": 160.0,
        "weight_g": 3500.0,
        "conservation_status": "Least Concern",
        "description": "Tallest flamingo species. Pink coloration comes from carotenoid pigments in their diet.",
        "region": "Africa, Southern Europe, South Asia",
        "diet": [
            {"food_type": "crustacean", "food_name": "Brine shrimp", "percentage": 50, "season": "year-round"},
            {"food_type": "algae", "food_name": "Blue-green algae", "percentage": 30, "season": "year-round"},
            {"food_type": "insect", "food_name": "Fly larvae", "percentage": 20, "season": "summer"},
        ],
        "sightings": [
            {"location": "Camargue, France", "latitude": 43.45, "longitude": 4.55, "observer_name": "Jean Dupont"},
            {"location": "Lake Nakuru, Kenya", "latitude": -0.37, "longitude": 36.09, "observer_name": "Wanjiku M."},
        ],
    },
    {
        "common_name": "Kiwi",
        "scientific_name": "Apteryx mantelli",
        "family": "Apterygidae",
        "habitat": "Temperate and subtropical forests",
        "wingspan_cm": 0.0,
        "weight_g": 2800.0,
        "conservation_status": "Vulnerable",
        "description": "Flightless bird endemic to New Zealand. Unusual nostrils at tip of beak for strong sense of smell.",
        "region": "New Zealand",
        "diet": [
            {"food_type": "insect", "food_name": "Earthworms", "percentage": 40, "season": "year-round"},
            {"food_type": "insect", "food_name": "Beetle larvae", "percentage": 25, "season": "year-round"},
            {"food_type": "fruit", "food_name": "Fallen berries", "percentage": 20, "season": "fall"},
            {"food_type": "crustacean", "food_name": "Freshwater crayfish", "percentage": 15, "season": "summer"},
        ],
        "sightings": [
            {"location": "Zealandia Sanctuary, Wellington, NZ", "latitude": -41.29, "longitude": 174.75, "observer_name": "Tane Paku"},
        ],
    },
    {
        "common_name": "Andean Condor",
        "scientific_name": "Vultur gryphus",
        "family": "Cathartidae",
        "habitat": "Mountains and coastal areas",
        "wingspan_cm": 320.0,
        "weight_g": 12000.0,
        "conservation_status": "Vulnerable",
        "description": "One of the world's largest flying birds. Can soar for hours without flapping wings.",
        "region": "South America",
        "diet": [
            {"food_type": "carrion", "food_name": "Deer carcass", "percentage": 50, "season": "year-round"},
            {"food_type": "carrion", "food_name": "Llama carcass", "percentage": 30, "season": "year-round"},
            {"food_type": "carrion", "food_name": "Sea lion carcass", "percentage": 20, "season": "summer"},
        ],
        "sightings": [
            {"location": "Colca Canyon, Peru", "latitude": -15.61, "longitude": -71.88, "observer_name": "Carlos Quispe"},
        ],
    },
    {
        "common_name": "Mandarin Duck",
        "scientific_name": "Aix galericulata",
        "family": "Anatidae",
        "habitat": "Wooded ponds, marshes, rivers",
        "wingspan_cm": 73.0,
        "weight_g": 570.0,
        "conservation_status": "Least Concern",
        "description": "Stunningly ornate plumage in males. Symbol of love and fidelity in East Asian culture.",
        "region": "East Asia",
        "diet": [
            {"food_type": "seed", "food_name": "Acorns", "percentage": 35, "season": "fall"},
            {"food_type": "plant", "food_name": "Aquatic plants", "percentage": 25, "season": "year-round"},
            {"food_type": "insect", "food_name": "Water beetles", "percentage": 20, "season": "summer"},
            {"food_type": "mollusk", "food_name": "Snails", "percentage": 20, "season": "spring"},
        ],
        "sightings": [
            {"location": "Central Park, New York, NY", "latitude": 40.78, "longitude": -73.97, "observer_name": "Sarah Kim"},
        ],
    },
    {
        "common_name": "Hoopoe",
        "scientific_name": "Upupa epops",
        "family": "Upupidae",
        "habitat": "Open grasslands, orchards, parkland",
        "wingspan_cm": 46.0,
        "weight_g": 62.0,
        "conservation_status": "Least Concern",
        "description": "Distinctive crown of feathers and long curved bill. Named for its 'oop-oop-oop' call.",
        "region": "Europe, Asia, Africa",
        "diet": [
            {"food_type": "insect", "food_name": "Crickets", "percentage": 40, "season": "year-round"},
            {"food_type": "insect", "food_name": "Beetle larvae", "percentage": 30, "season": "spring"},
            {"food_type": "insect", "food_name": "Ants", "percentage": 20, "season": "summer"},
            {"food_type": "amphibian", "food_name": "Small lizards", "percentage": 10, "season": "summer"},
        ],
        "sightings": [
            {"location": "Algarve, Portugal", "latitude": 37.02, "longitude": -7.93, "observer_name": "Joao Silva"},
            {"location": "Rajasthan, India", "latitude": 26.92, "longitude": 75.79, "observer_name": "Priya Sharma"},
        ],
    },
]


def seed_database():
    Base.metadata.create_all(bind=engine)
    db = SessionLocal()

    if db.query(Bird).count() > 0:
        db.close()
        return

    for bird_data in BIRDS:
        diet_data = bird_data.pop("diet")
        sighting_data = bird_data.pop("sightings")

        bird = Bird(**bird_data)
        db.add(bird)
        db.flush()

        for d in diet_data:
            db.add(DietItem(bird_id=bird.id, **d))

        for s in sighting_data:
            db.add(Sighting(bird_id=bird.id, observed_at=datetime.datetime.utcnow(), **s))

    db.commit()
    db.close()
