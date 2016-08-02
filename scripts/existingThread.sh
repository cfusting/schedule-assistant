curl -X POST -H "Content-Type: application/json" -d '{
  "setting_type" : "call_to_actions",
  "thread_state" : "existing_thread",
  "call_to_actions":[
    {
      "type":"postback",
      "title":"Schedule",
      "payload":"schedule"
    },
    {
      "type":"postback",
      "title":"Cancel",
      "payload":"cancel"
    },
    {
      "type":"postback",
      "title":"View",
      "payload":"view"
    }
  ]
}' "https://graph.facebook.com/v2.6/me/thread_settings?access_token=EAAWOb6Mv7N4BAHm2DtLK0Lof1bzSWe05AjingXRftVZCHRK4afHOidLS8FKun10jugWrCWPoZCjnbsdKkBXHTTsI0Vt1Sn34SxmrgyQhEML72TylxrQNUyXXduitbNPUoCEeuE9D5PQODrjWNW5lVKknfukzGcJuMjoCzi3gZDZD"
