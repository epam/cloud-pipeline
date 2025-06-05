import sqlite3

conn = sqlite3.connect('chatbot.db')
conn.execute("PRAGMA foreign_keys = ON")

conn.execute('''
CREATE TABLE chats (
    chat_id INTEGER PRIMARY KEY AUTOINCREMENT,
    title TEXT
)
''')
conn.execute('''
CREATE TABLE messages (
    message_id INTEGER PRIMARY KEY AUTOINCREMENT,
    chat_id INTEGER,
    created_date TEXT,
    role TEXT,
    content TEXT,
    attributes TEXT,
    FOREIGN KEY (chat_id) REFERENCES chats(chat_id)
)
''')
conn.commit()
conn.close()
