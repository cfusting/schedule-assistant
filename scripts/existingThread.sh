curl -X POST -H "Content-Type: application/json" -d '{
  "setting_type" : "call_to_actions",
  "thread_state" : "existing_thread",
  "call_to_actions":[
    {
      "type":"postback",
      "title":"Schedule a Lesson",
      "payload":"schedule"
    },
    {
      "type":"postback",
      "title":"Cancel a Lesson",
      "payload":"cancel"
    },
    {
      "type":"postback",
      "title":"View a Scheduled Lesson",
      "payload":"view"
    }
  ]
}' "https://graph.facebook.com/v2.6/me/thread_settings?access_token=EAAWOb6Mv7N4BAMtRoYHBaLBMZCyZAGDWmwFZCKr1KarrJUy3ZAHmkGd1OVAjUBdJzAwbYZAyiZCFYJqjWZBLyjvJILTjXhLz95q5lGdAe1NWUcKWSixKeCNumHEAZBZAkh1EWNOjOYiiGv1jiUZCttghM0ZCF4Ppv79MVZBjibuwmFQ6wQZDZD"
