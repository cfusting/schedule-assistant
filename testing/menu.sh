curl -k -X POST -H "Content-Type: application/json" -d '{
    "object":"page",
    "entry":[
    {
      "id":"483961371799675",
      "time":1464060164715,
      "messaging":[
      {
        "sender":{
            "id":"624734821115"
        },
        "recipient":{
            "id":"483961371799675"
        },
        "timestamp":1464060164691,
        "message":{
            "mid":"mid.1464060164681:a9e58e9d663049f946",
            "seq":5,
            "text":"menu"
        }
        }
    ]
  }
  ]
}' --include "https://localhost:9443/webhook"

