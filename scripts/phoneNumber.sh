curl -X POST -H "Content-Type: application/json" -d '{
  "recipient":{
    "phone_number":"+1(828)772-0012"
  },
  "message":{
    "text":"hello, world!"
  }
}' "https://graph.facebook.com/v2.6/me/messages?access_token=EAAWOb6Mv7N4BAHmjDThoNuyYD8lhG4wldZAK3VGW585AHikKc6Dib2z3hzWCZB2BHXHEZCmOtjvo2eQAWVgpGYSNf1ngZBWiZAHaYCUR77uMrMkfl0UNcPgKx320HHrQ3h6xpaSO2nmPcKHHBdbrdC4VLSubOPkjeDrZAH079eMAZDZD"
