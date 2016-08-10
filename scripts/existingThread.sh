curl -X POST -H "Content-Type: application/json" -d '{
  "setting_type" : "call_to_actions",
  "thread_state" : "existing_thread",
  "call_to_actions":[
    {
      "type":"postback",
      "title":"Schedule",
      "payload":"{"action":"schedule"}"
    },
    {
      "type":"postback",
      "title":"Cancel",
      "payload":"{"action":"cancel"}"
    },
    {
      "type":"postback",
      "title":"View",
      "payload":"{"action":"view"}"
    }
  ]
}' "https://graph.facebook.com/v2.6/me/thread_settings?access_token=EAAWOb6Mv7N4BANieML0JdiljZAt3yPvkngJqfyFC9746gE5J7TkU7xVbDm8q1ZBaZALW77S7TL80rpBEZCGy3WENG8NHxE7D0H0OsKZB9FiVyZBfOhmzoNx3YXZA2ngTb7ZCglvlMZAWWtEIyu9RCJoPI2bGsM7EK0xwZD"
