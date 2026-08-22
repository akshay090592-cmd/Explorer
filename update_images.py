import re
import os
import urllib.request
import urllib.parse
import json

API_KEY = os.environ.get("GOOGLE_PLACES_API_KEY", "")
DATABASE_FILE = "app/src/main/java/com/example/malaysiaitinerary/data/local/AppDatabase.kt"
IMAGES_DIR = "app/src/main/assets/images"

def fetch_json(url):
    req = urllib.request.Request(url)
    with urllib.request.urlopen(req) as response:
        return json.loads(response.read().decode())

def fetch_image(url):
    req = urllib.request.Request(url)
    with urllib.request.urlopen(req) as response:
        return response.read()

def main():
    if not os.path.exists(IMAGES_DIR):
        os.makedirs(IMAGES_DIR)

    with open(DATABASE_FILE, 'r') as f:
        content = f.read()

    pattern = re.compile(r'googleMapsUrl\s*=\s*"https://www.google.com/maps/search/\?api=1&query=([^"]+)".*?imageUrl\s*=\s*"file:///android_asset/images/([^"]+)"', re.DOTALL)
    
    matches = pattern.findall(content)
    
    valid_images = set()

    print(f"Found {len(matches)} locations to fetch images for.")

    for idx, (query, image_filename) in enumerate(matches):
        valid_images.add(image_filename)
        query_decoded = urllib.parse.unquote(query)
        print(f"[{idx+1}/{len(matches)}] Fetching image for query: {query_decoded} -> {image_filename}")
        
        search_url = f"https://maps.googleapis.com/maps/api/place/textsearch/json?query={query}&key={API_KEY}"
        
        try:
            res = fetch_json(search_url)
            photo_ref = None
            
            if res.get('results') and len(res['results']) > 0:
                photos = res['results'][0].get('photos')
                if photos and len(photos) > 0:
                    photo_ref = photos[0].get('photo_reference')

            image_data = None
            if photo_ref:
                photo_url = f"https://maps.googleapis.com/maps/api/place/photo?maxwidth=800&photoreference={photo_ref}&key={API_KEY}"
                
                try:
                    image_data = fetch_image(photo_url)
                except Exception as e:
                    print(f"  Failed grabbing photo via Places API: {e}")
            
            if not image_data:
                print(f"  Falling back to static map...")
                static_url = f"https://maps.googleapis.com/maps/api/staticmap?center={query}&zoom=14&size=800x600&key={API_KEY}"
                image_data = fetch_image(static_url)

            if image_data:
                file_path = os.path.join(IMAGES_DIR, image_filename)
                with open(file_path, 'wb') as img_f:
                    img_f.write(image_data)
                print(f"  Saved {image_filename}")
            else:
                print(f"  Failed to fetch image data for {query_decoded}")

        except Exception as e:
            print(f"  Error processing {query_decoded}: {e}")

    print("Checking for unused images...")
    for filename in os.listdir(IMAGES_DIR):
        if filename.endswith('.jpg') or filename.endswith('.png'):
            if filename not in valid_images:
                file_path = os.path.join(IMAGES_DIR, filename)
                os.remove(file_path)
                print(f"Removed unused image: {filename}")

    print("Done!")

if __name__ == "__main__":
    main()
