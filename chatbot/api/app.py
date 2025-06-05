from flask import Flask, request, jsonify
from datetime import datetime
import sqlite3

app = Flask(__name__)
DATABASE = 'chatbot.db'

def get_db_connection():
    conn = sqlite3.connect(DATABASE)
    conn.row_factory = sqlite3.Row
    return conn

@app.route('/')
def home():
    return 'Flask + SQLite API is running!'

@app.route('/chat', methods=['POST'])
def add_chat():
    data = request.get_json()
    title = data.get('title', 'Untitled')
    conn = get_db_connection()
    cursor = conn.cursor()
    cursor.execute('INSERT INTO chats (title) VALUES (?)', (title,))
    chat_id = cursor.lastrowid
    conn.commit()
    conn.close()
    return jsonify(chat_id)

@app.route('/chat/<chat_id>', methods=['GET'])
def get_chat(chat_id):
    conn = get_db_connection()
    result = conn.execute('SELECT * FROM chats WHERE chat_id = ?', (chat_id,)).fetchall()
    conn.close()
    if result is None:
        return jsonify({"error": "Chat not found"}), 404
    return jsonify([dict(row) for row in result])

@app.route('/chat/message/<chat_id>', methods=['POST'])
def add_message(chat_id):
    data = request.get_json()
    role = data['role']
    content = data['content']
    attributes = data['attributes']
    conn = get_db_connection()
    cursor = conn.cursor()
    cursor.execute('INSERT INTO messages (chat_id, created_date, role, content, attributes) VALUES (?,?,?,?,?)',
                   (chat_id, datetime.now(), role, content, attributes))
    message_id = cursor.lastrowid
    conn.commit()
    conn.close()
    return jsonify(message_id)

@app.route('/chat/message/<message_id>', methods=['DELETE'])
def delete_message(message_id):
    conn = get_db_connection()
    conn.execute('DELETE FROM messages WHERE message_id = ?', (message_id,))
    conn.commit()
    conn.close()
    return jsonify(message_id)

@app.route('/chat/message/<message_id>', methods=['GET'])
def get_message(message_id):
    conn = get_db_connection()
    result = conn.execute('SELECT * FROM messages WHERE message_id = ?', (message_id,)).fetchall()
    conn.close()
    return jsonify([dict(row) for row in result])

@app.route('/chat/<chat_id>/messages', methods=['GET'])
def get_messages(chat_id):
    conn = get_db_connection()
    result = conn.execute('SELECT * FROM messages WHERE chat_id = ? ORDER BY created_date ASC', (chat_id,)).fetchall()
    conn.close()
    return jsonify([dict(row) for row in result])

if __name__ == '__main__':
    app.run(debug=True)
