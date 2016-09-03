curl -X POST -H "Content-Type: application/json" -d '{
  "setting_type":"call_to_actions",
  "thread_state":"new_thread",
  "call_to_actions":[
    {
      "payload":"{\"action\":\"menu\"}"
    }
  ]
}' "https://graph.facebook.com/v2.6/me/thread_settings?access_token=EAAWOb6Mv7N4BAC5ZAPc335ZB2MZAIS9HFjCZBgkdT4iYzutr7Vx4reFz9uelIZAJnuCuXmXbKJNlxNCZB7blIn1BffjJVVsu7P1es4YLGyQqZBhZCq7KOd49DJHS3ZBqt2YCUMKorsAUkTzH6YgRqu7EntCAMCzn85beWkcBU3HjxFwZDZD"
