from flask import Flask, jsonify, send_from_directory, request, url_for, abort
import os
import random
import uuid
import sqlite3

app = Flask(__name__)

# Configure the directory where images are stored
IMAGE_FOLDER = '/home/mersadesalati/images/'
app.config['IMAGE_FOLDER'] = IMAGE_FOLDER

# Ensure the image folder exists
if not os.path.exists(IMAGE_FOLDER):
    os.makedirs(IMAGE_FOLDER)

DB_FILE = '/home/mersadesalati/database.db'
app.config['DB_FILE'] = DB_FILE
def init_db():
    conn = sqlite3.connect(app.config['DB_FILE'])
    c = conn.cursor()
    c.execute('''CREATE TABLE IF NOT EXISTS images
                 (id TEXT PRIMARY KEY, filename TEXT)''')
    c.execute('''CREATE TABLE IF NOT EXISTS scores
                 (image_id TEXT, score INTEGER,
                  FOREIGN KEY (image_id) REFERENCES images (id))''')

    images = os.listdir(app.config['IMAGE_FOLDER'])
    for image in images:
        image_id = str(uuid.uuid4())
        conn.execute('INSERT INTO images (id, filename) VALUES (?, ?)', (image_id, image))
    conn.commit()
    conn.close()
if not os.path.exists(app.config['DB_FILE']):
    init_db()

def get_db_connection():
    conn = sqlite3.connect(DB_FILE)
    conn.row_factory = sqlite3.Row
    return conn

@app.route('/api/random_images/<int:num_images>', methods=['GET'])
def get_random_images(num_images):
    conn = get_db_connection()
    images = conn.execute('SELECT id, filename FROM images').fetchall()
    conn.close()

    if not images:
        return jsonify({"error": "No images found"}), 404

    random_images = random.sample(images, min(num_images, len(images)))
    response = {
        "items": [{"id": img["id"], "url": url_for('get_image', filename=img["filename"], _external=True)} for img in random_images]
    }

    return jsonify(response)

@app.route('/api/image/<path:filename>', methods=['GET'])
def get_image(filename):
    print("File location using os.getcwd():", os.getcwd())
    file_path = os.path.join(app.config['IMAGE_FOLDER'], filename)
    if not os.path.exists(file_path):
        abort(404, description="Resource not found")
    return send_from_directory(app.config['IMAGE_FOLDER'], filename)

@app.route('/api/submit_score', methods=['POST'])
def submit_score():
    data = request.get_json()
    image_id = data.get('image_id')
    score = data.get('score')

    if not image_id or not isinstance(score, int):
        return jsonify({"error": "Invalid data"}), 400

    conn = get_db_connection()
    conn.execute('INSERT INTO scores (image_id, score) VALUES (?, ?)', (image_id, score))
    conn.commit()
    conn.close()

    return jsonify({"message": "Score submitted successfully"}), 201

@app.route('/api/scores/<image_id>', methods=['GET'])
def get_scores(image_id):
    conn = get_db_connection()
    scores = conn.execute('SELECT score FROM scores WHERE image_id = ?', (image_id,)).fetchall()
    conn.close()

    if not scores:
        return jsonify({"error": "No scores found for this image"}), 404

    score_list = [score['score'] for score in scores]
    return jsonify({"image_id": image_id, "scores": score_list})

if __name__ == '__main__':
    app.run(debug=True)