"""
Reproducible SYNTHETIC notification dataset for Attune.

Every example is generated from hand-written templates (see TEMPLATES below). Labels are annotated per template by the
author, not measured from real users. The dataset is designed to be deliberately non-trivial:
  * the same sender (e.g. "Mom", "HDFC Bank", "Prof. Sharma") appears with casual, urgent and ambiguous intents;
  * promotions use fake urgency ("ends in 1 hour!") and some casual chats misuse the word "urgent";
  * English and Hinglish, abbreviations, misspellings, links, link-only and very short messages are included.

Usage:  python ml/generate_dataset.py            (writes ml/data/notifications.csv)
Seeded: the same seed always produces the same file.
"""
import csv
import os
import random
import re

SEED = 20261026
VARIANTS_PER_TEMPLATE = 12
OUT = os.path.join(os.path.dirname(__file__), "data", "notifications.csv")

# ---------------------------------------------------------------- apps (package, label, category) ----
# Categories must match com.attune.app.util.AppCategoryMapper, which the app uses at inference time.
APPS = {
    "wa": ("com.whatsapp", "WhatsApp", "messaging"),
    "sms": ("com.google.android.apps.messaging", "Messages", "messaging"),
    "tg": ("org.telegram.messenger", "Telegram", "messaging"),
    "ig": ("com.instagram.android", "Instagram", "social"),
    "fb": ("com.facebook.katana", "Facebook", "social"),
    "x": ("com.twitter.android", "X", "social"),
    "snap": ("com.snapchat.android", "Snapchat", "social"),
    "li": ("com.linkedin.android", "LinkedIn", "social"),
    "yt": ("com.google.android.youtube", "YouTube", "short_video"),
    "gmail": ("com.google.android.gm", "Gmail", "email"),
    "outlook": ("com.microsoft.office.outlook", "Outlook", "email"),
    "slack": ("com.slack", "Slack", "productivity"),
    "teams": ("com.microsoft.teams", "Teams", "productivity"),
    "cal": ("com.google.android.calendar", "Calendar", "productivity"),
    "classroom": ("com.google.android.apps.classroom", "Classroom", "education"),
    "hdfc": ("com.snapwork.hdfc", "HDFC Bank", "finance"),
    "paytm": ("net.one97.paytm", "Paytm", "finance"),
    "gpay": ("com.google.android.apps.nbu.paisa.user", "Google Pay", "finance"),
    "amazon": ("com.amazon.mShop.android.shopping", "Amazon", "shopping"),
    "flipkart": ("com.flipkart.android", "Flipkart", "shopping"),
    "myntra": ("com.myntra.android", "Myntra", "shopping"),
    "swiggy": ("in.swiggy.android", "Swiggy", "shopping"),
    "news": ("com.inshorts", "Inshorts", "news"),
    "dialer": ("com.google.android.dialer", "Phone", "calls"),
    "gms": ("com.google.android.gms", "Google", "system"),
    "game": ("com.supercell.clashofclans", "Clash of Clans", "gaming"),
    "spotify": ("com.spotify.music", "Spotify", "entertainment"),
}

# ---------------------------------------------------------------- slot values ----
SLOTS = {
    "friend": ["Rahul", "Priya", "Aman", "Sneha", "Kabir", "Ananya", "Rohan", "Meera", "Arjun", "Zoya", "Dev", "Isha"],
    "family": ["Mom", "Mummy", "Dad", "Papa", "Didi", "Bhaiya", "Nani", "Chachu"],
    "group": ["CSE 3rd Year", "Hostel B Wing", "Family", "Cousins Gang", "Project Team", "Cricket Squad", "Flat 402"],
    "prof": ["Prof. Sharma", "Dr. Iyer", "Prof. Mehta", "HOD CSE", "Exam Cell", "Training & Placement Cell"],
    "colleague": ["Neha (Team Lead)", "Vikram", "Sana", "Manager", "On-call Bot", "HR Team"],
    "course": ["DBMS", "Operating Systems", "Linear Algebra", "Computer Networks", "ML Lab", "Economics"],
    "time": ["5 pm", "10:30 am", "3 pm", "tonight 9", "11:59 PM", "noon", "4:15 pm"],
    "day": ["tomorrow", "Monday", "Friday", "this weekend", "on the 15th"],
    "amount": ["1,250", "499", "12,340", "2,500", "89", "45,000", "3,999"],
    "acct": ["XX1234", "XX9087", "XX4421", "XX0067"],
    "code": None,  # random digits
    "num": None,
    "item": ["wireless earbuds", "running shoes", "phone cover", "textbook", "kurta", "water bottle", "backpack"],
    "creator": ["techburner", "carryminati", "mkbhd", "dhruvrathee", "nas.daily", "foodie_delhi", "cricbuzz"],
    "show": ["a new reel", "a new video", "a live stream", "a story", "a short"],
    "headline": ["Sensex jumps 600 points", "Monsoon to arrive early this year", "India wins the series 3-1",
                 "New metro line opens next week", "Gold prices hit record high", "Exam schedule for boards announced"],
    "company": ["Infosys", "Razorpay", "Zomato", "a startup", "TCS", "Flipkart"],
    "place": ["main gate", "library", "canteen", "hostel", "metro station", "parking"],
    "game": ["Clash of Clans", "BGMI", "Ludo King", "Free Fire"],
}

URLS = {
    # (template, link_safety gold label). "unknown" = no local evidence either way. "caution" = concrete local signal.
    "known": [("https://www.amazon.in/dp/B0{r}", "unknown"), ("https://classroom.google.com/c/{r}", "unknown"),
              ("https://meet.google.com/{a}-{b}-{c}", "unknown"), ("https://youtu.be/{r}", "unknown"),
              ("https://github.com/attune/{r}/pull/42", "unknown"), ("https://docs.google.com/document/d/{r}", "unknown")],
    "shortener": [("https://bit.ly/3{r}", "unknown"), ("https://tinyurl.com/{r}", "unknown"), ("http://t.co/{r}", "unknown")],
    "suspicious": [("http://192.168.{n}.{n}/login", "caution"), ("http://hdfc-secure-verify.top/kyc/{r}", "caution"),
                   ("https://amaz0n-rewards.xyz/claim?id={r}", "caution"), ("http://paytm-kyc-update.live/{r}", "caution"),
                   ("https://xn--pypal-4ve.com/verify/{r}", "caution"), ("http://free-recharge-offer.click/{r}", "caution")],
}

# ---------------------------------------------------------------- templates ----
# Each entry: family, app keys, sender slot (or fixed list), patterns, labels (importance L/M/H, urgent, distracting, action), flags.
# Every pattern becomes its own template_id, so near-duplicates never cross train/validation/test splits.
T = []
def fam(family, apps, sender, patterns, imp, urg, dis, act, lang="en", ambiguous=False, url=None):
    T.append(dict(family=family, apps=apps, sender=sender, patterns=patterns, imp=imp, urg=urg, dis=dis, act=act, lang=lang, ambiguous=ambiguous, url=url))

# --- casual conversation (same senders also appear in urgent families below)
fam("casual_chat", ["wa", "tg", "sms"], "friend", [
    "haha did you see that meme I sent", "what's up, bored in class", "bro this song is so good, listen",
    "lol look at this", "are you watching the match?", "guess who I just saw at the mall", "good morning!! have a nice day",
    "check out this reel, so funny", "we should plan a trip this weekend", "I just finished that series, the ending was wild",
    "send me that photo from yesterday when you're free", "lmao that teacher today", "happy birthday to your brother from me!",
    "what are you doing tonight?", "new episode is out, no spoilers pls"], "L", 0, 1, 0)
fam("casual_chat_hinglish", ["wa", "tg"], "friend", [
    "kya kar raha hai bhai", "yaar aaj bahut bore ho raha hu", "scene kya hai aaj raat ka", "bhai ye video dekh pagal hai",
    "chal na movie chalte hai weekend pe", "kal canteen me kya hua tha bata", "haha mast tha yaar", "free hai kya baat karni thi time pass",
    "oye match dekh raha hai?", "mood off hai yaar kuch funny bhej", "aaj ka plan kya hai", "teri DP mast hai"], "L", 0, 1, 0, lang="hinglish")
fam("casual_family", ["wa", "sms"], "family", [
    "did you eat lunch?", "call when free, nothing urgent", "your cousin's wedding photos are on the family group",
    "Nani says hello", "remember to drink water beta", "we are going to the market, need anything?", "good night beta",
    "khana kha liya?", "aaj kya banaya hostel me", "dekho ye recipe try karna"], "L", 0, 1, 0)
fam("group_chatter", ["wa", "tg"], "group", [
    "{friend}: 😂😂😂", "{friend}: who's coming for chai at 5", "{friend}: sent a sticker", "{friend}: anyone has the netflix password",
    "{friend}: guys look at this", "{friend}: happy birthday {friend}!!! 🎉", "{friend}: lol same", "{friend}: kal party pakka?",
    "{friend}: pic dekho trip ki", "{friend}: good morning everyone"], "L", 0, 1, 0)
fam("fake_urgent_casual", ["wa", "tg"], "friend", [
    "URGENT: need the best memes asap lol", "urgent!!! which shoes should I buy 😂", "emergency: I'm so bored, talk to me",
    "asap reply, which movie tonight?", "urgent question: pizza or burger?", "bhai urgent hai… meme dekh"], "L", 0, 1, 1)

# --- urgent personal (same senders as casual)
fam("urgent_personal", ["wa", "sms", "tg"], "family", [
    "call me right now, it's urgent", "Nani is in the hospital, please call immediately", "where are you?? pick up the phone",
    "accident on the way home, I'm okay but please call", "come home now, papa is not well", "please call back, emergency",
    "your phone is off, call me asap it's important", "I'm at the hospital with dad, call me"], "H", 1, 0, 1)
fam("urgent_personal_hinglish", ["wa", "sms"], "family", [
    "jaldi call karo urgent hai", "phone utha, emergency hai", "ghar aaja abhi, papa ki tabiyat kharab hai",
    "hospital me hai nani, turant call kar", "kaha hai tu? bahut zaroori baat hai call kar"], "H", 1, 0, 1, lang="hinglish")
fam("urgent_friend", ["wa", "sms", "tg"], "friend", [
    "bro I need help, got into an accident near the {place}", "I'm locked out and my phone is dying, can you come to the {place}",
    "can you call me now? something happened", "please pick up, I'm stuck at the {place} and need help",
    "bhai urgent help chahiye, call kar abhi", "lost my wallet and phone battery is 2%, come to {place} please"], "H", 1, 0, 1)
fam("missed_calls", ["dialer"], ["Missed call"], [
    "3 missed calls from {family}", "2 missed calls from {family}", "4 missed calls from {friend}"], "H", 1, 0, 1)
fam("missed_call_single", ["dialer"], ["Missed call"], [
    "Missed call from {friend}", "Missed call from {family}", "Missed call from +91 98{num}"], "M", 0, 0, 1)
fam("personal_needs_reply", ["wa", "sms"], "family", [
    "can you send the rent split by tonight?", "please transfer the fees today, last date", "did you book the train tickets? reply when you can",
    "send me your aadhaar photo for the form", "which date works for the function? tell me by evening"], "M", 0, 0, 1)
fam("friend_needs_reply", ["wa", "tg"], "friend", [
    "can you share the {course} notes before the exam?", "are you coming to the project meeting at {time}?",
    "need your part of the ppt by tonight", "kal exam hai, notes bhej de please", "did you submit the form? tell me the link"], "M", 0, 0, 1)

# --- college
fam("college_deadline_urgent", ["classroom", "gmail"], "prof", [
    "Reminder: {course} assignment is due today at 11:59 PM", "{course} lab submission closes in 1 hour",
    "Quiz for {course} starts in 15 minutes, join now", "Last chance: submit the {course} project report by {time} today",
    "Exam hall allotment released. Report to the exam hall in 30 minutes", "Your {course} viva is in 20 minutes, room 204"], "H", 1, 0, 1)
fam("college_deadline_soon", ["classroom", "gmail"], "prof", [
    "{course} assignment 3 is due {day}", "New assignment posted in {course}: due {day}", "Submit the {course} mini project proposal by {day}",
    "Registration for the internship drive closes {day}", "Please fill the course feedback form by {day}"], "M", 0, 0, 1)
fam("college_announcement", ["classroom", "gmail", "wa"], "prof", [
    "The library will remain closed on Sunday", "Guest lecture on AI ethics in the auditorium on {day}",
    "Updated timetable uploaded for next semester", "Sports day registrations are open", "Cultural fest volunteers meeting {day}",
    "Holiday notice: college closed on account of the festival", "Slides from today's {course} lecture are uploaded"], "L", 0, 0, 0)
fam("college_class_change", ["classroom", "wa"], "prof", [
    "Today's {course} class is moved to room 301 at {time}", "{course} class cancelled today", "Extra class for {course} at {time} today, attendance compulsory"], "M", 1, 0, 0)

# --- work
fam("work_incident", ["slack", "teams"], "colleague", [
    "Prod is down, need you on the incident call now", "PagerDuty: high error rate on payments API, please ack",
    "Client demo in 10 minutes and the build is failing, can you check?", "urgent: the release is blocked on your approval",
    "Server alert: disk 95% full on db-01"], "H", 1, 0, 1)
fam("work_request", ["slack", "teams", "gmail", "outlook"], "colleague", [
    "Can you review my PR when you get a chance?", "Please update the sprint board by end of day", "Could you share the slides before Friday?",
    "When you're free, can we sync about the roadmap?", "No rush, but can you look at the bug I assigned you?", "Please fill your timesheet this week"], "M", 0, 0, 1)
fam("work_chatter", ["slack", "teams"], "colleague", [
    "lunch anyone? 🍕", "Happy Friday everyone!", "Who's coming for the team outing?", "Check out this article on productivity",
    "{friend} reacted 👍 to your message", "New channel #random-memes created"], "L", 0, 1, 0)
fam("work_meeting", ["cal", "teams", "outlook"], ["Calendar", "Meeting reminder"], [
    "Standup starts in 5 minutes", "Interview with {company} in 10 minutes – join link inside", "1:1 with manager starts now",
    "Design review in 15 minutes, room Orion"], "H", 1, 0, 1)
fam("job_updates", ["gmail", "li"], ["Careers", "{company} Recruiting", "LinkedIn"], [
    "Your interview with {company} is scheduled for {day} at {time}. Please confirm", "Congratulations! You are shortlisted for the next round at {company}",
    "Action required: complete the online assessment for {company} by {day}"], "H", 0, 0, 1)
fam("job_spam", ["li", "gmail"], ["LinkedIn", "Jobs Alert"], [
    "5 new jobs for you in Bengaluru", "{friend} viewed your profile", "You appeared in 12 searches this week",
    "People are talking about AI in the workplace", "Congrats {friend} on the work anniversary!"], "L", 0, 1, 0)

# --- reminders / appointments
fam("appointment_now", ["cal", "sms"], ["Reminder", "Calendar", "Apollo Clinic"], [
    "Dentist appointment in 30 minutes", "Your doctor's appointment is at {time} today, please arrive 10 min early",
    "Reminder: take your medicine now", "Flight boarding starts in 40 minutes at gate 12", "Your cab is arriving in 2 minutes"], "H", 1, 0, 1)
fam("appointment_later", ["cal", "sms", "gmail"], ["Reminder", "Calendar", "Apollo Clinic"], [
    "Appointment confirmed for {day} at {time}", "Reminder: haircut appointment {day}", "Your passport appointment is on the 15th",
    "Don't forget: parents' anniversary {day}", "Gym membership renews next week"], "M", 0, 0, 0)

# --- OTP / security / banking
fam("otp", ["sms", "hdfc", "paytm", "gpay", "amazon"], ["AX-HDFCBK", "VM-PAYTM", "Google Pay", "AD-AMAZON", "Bank OTP"], [
    "{code} is your OTP for login. Do not share it with anyone", "Your one-time password is {code}. Valid for 10 minutes",
    "Use verification code {code} to sign in", "OTP for transaction of Rs {amount} is {code}. Never share OTP",
    "{code} is your Amazon OTP. Do not share", "Your login code: {code}"], "H", 1, 0, 1)
fam("security_alert", ["gms", "gmail", "hdfc"], ["Google", "Security alert", "HDFC Bank"], [
    "New sign-in to your account from a Windows device. If this wasn't you, secure your account",
    "Your password was changed. If you didn't do this, reset it now", "Unusual transaction of Rs {amount} detected on card {acct}. Reply NO if not you",
    "Someone tried to sign in with your password. Review activity now", "Your card {acct} was blocked after 3 wrong PIN attempts"], "H", 1, 0, 1)
fam("bank_txn", ["hdfc", "sms", "paytm", "gpay"], ["HDFC Bank", "AX-HDFCBK", "Paytm", "Google Pay"], [
    "Rs {amount} debited from a/c {acct} on UPI. Avl bal Rs {amount}", "Rs {amount} credited to your account {acct}",
    "You paid Rs {amount} to {friend}", "{friend} sent you Rs {amount}", "Salary of Rs {amount} credited to a/c {acct}"], "M", 0, 0, 0)
fam("bank_bill", ["hdfc", "paytm", "sms"], ["HDFC Bank", "Paytm", "Electricity Board"], [
    "Your credit card bill of Rs {amount} is due on 5th", "Electricity bill of Rs {amount} due {day}. Pay to avoid late fee",
    "Your loan EMI of Rs {amount} will be debited {day}"], "M", 0, 0, 1)
fam("bank_promo", ["hdfc", "paytm", "gpay"], ["HDFC Bank", "Paytm", "Google Pay"], [
    "Get a pre-approved personal loan of Rs 5 lakh instantly!", "Earn 5x reward points on dining this weekend",
    "Scratch card waiting for you! Open now", "Win cashback up to Rs 500 on your next recharge", "Upgrade to our premium credit card, zero joining fee"], "L", 0, 1, 0)

# --- shopping / promotions (fake urgency on purpose)
fam("promotion", ["amazon", "flipkart", "myntra", "swiggy"], ["Amazon", "Flipkart", "Myntra", "Swiggy"], [
    "Flat 50% off on {item}! Sale ends tonight", "LAST CHANCE: offer expires in 1 hour ⏰", "Your cart is waiting – {item} is almost sold out",
    "Big Billion Days are LIVE. Grab deals now!!", "Hungry? 60% off on your favourite restaurants", "Price drop alert on {item}",
    "Only 2 left in stock! Buy {item} before it's gone", "Exclusive deal just for you, ends in 15 minutes", "Weekend sale: buy 1 get 1 free"], "L", 0, 1, 0)
fam("promotion_hinglish", ["swiggy", "flipkart", "paytm"], ["Swiggy", "Flipkart", "Paytm"], [
    "Bhook lagi? Order karo aur pao 50% off", "Sale shuru ho gayi hai! Jaldi karo", "Aaj raat tak hi hai ye offer, miss mat karna",
    "Cashback ka maza, abhi recharge karo"], "L", 0, 1, 0, lang="hinglish")
fam("scam", ["sms", "wa"], ["+91 70{num}", "VK-ALERTS", "Lucky Draw", "KYC Team"], [
    "Congratulations! You won Rs 50,000 in lucky draw. Claim now {url}", "Your KYC will expire today. Update immediately at {url} or account will be blocked",
    "Dear customer your electricity will be disconnected tonight. Call 98{num} immediately", "You have a pending refund of Rs {amount}. Click {url} to receive",
    "Work from home and earn Rs 5000 daily. Join {url}"], "L", 0, 1, 0, url="suspicious")

# --- social / video / games / news
fam("social", ["ig", "fb", "x", "snap"], ["Instagram", "Facebook", "X", "Snapchat"], [
    "{friend} liked your photo", "{friend} started following you", "{friend} mentioned you in a comment", "You have 5 new notifications",
    "{creator} posted {show}", "{friend} sent you a snap", "See what {friend} and 8 others posted today", "Your post is getting more likes than usual",
    "{friend} added to their story", "Trending now: #{creator}"], "L", 0, 1, 0)
fam("video", ["yt"], ["YouTube"], [
    "{creator} uploaded: you won't believe what happened", "{creator} is live now", "Recommended: 10 tips to study faster",
    "New from {creator}: reacting to fan comments", "Watch the highlights: India vs Australia"], "L", 0, 1, 0)
fam("games", ["game"], ["{game}"], [
    "Your energy is full! Come back and play", "Your village is under attack!", "Daily reward ready to claim", "Your friend {friend} beat your high score"], "L", 0, 1, 0)
fam("news", ["news"], ["Inshorts", "Breaking"], [
    "{headline}", "Breaking: {headline}", "Top stories today: {headline}", "Just in: {headline}"], "L", 0, 1, 0)
fam("music", ["spotify"], ["Spotify"], ["New release from your favourite artist", "Your Daily Mix is ready", "Your year in music is here"], "L", 0, 1, 0)

# --- delivery
fam("delivery_done", ["amazon", "flipkart", "swiggy"], ["Amazon", "Flipkart", "Swiggy"], [
    "Your order of {item} has been delivered", "Shipped: your {item} is on the way", "Your package will arrive {day}", "Order #{num} is out for delivery today"], "L", 0, 0, 0)
fam("delivery_now", ["amazon", "flipkart", "swiggy"], ["Amazon", "Flipkart", "Swiggy"], [
    "Delivery agent is arriving in 5 minutes. Share OTP {code}", "Your food is at the door, please collect", "Delivery attempt failed, agent is waiting at the {place}, call now"], "M", 1, 0, 1)

# --- email
fam("email_newsletter", ["gmail", "outlook"], ["Medium Daily Digest", "Newsletter", "Coursera"], [
    "Top 10 productivity hacks for students", "Your weekly digest is here", "New courses you might like", "This week in tech: AI everywhere"], "L", 0, 1, 0)
fam("email_important", ["gmail", "outlook"], ["Registrar Office", "{prof}", "Scholarship Cell"], [
    "Action required: your scholarship documents are incomplete", "Fee payment deadline is {day}. Late fee applies after that",
    "Your hostel allotment requires confirmation by {day}"], "M", 0, 0, 1)

# --- links (non-scam)
fam("link_share_casual", ["wa", "tg", "ig"], "friend", [
    "lol watch this {url}", "this is so you 😂 {url}", "dekh ye {url}", "{url} haha"], "L", 0, 1, 0, url="known")
fam("link_share_work", ["slack", "teams", "gmail"], "colleague", [
    "Here is the doc for tomorrow's meeting {url}", "Please review before EOD: {url}", "Meeting link: {url} (starts in 5 min)"], "M", 0, 0, 1, url="known")
fam("link_class", ["classroom", "wa"], "prof", ["Class is starting now, join here {url}", "Quiz link (closes in 10 min): {url}"], "H", 1, 0, 1, url="known")

# --- ambiguous / low context (gold: UNKNOWN, recommended review). Senders mix family, friends and unknown numbers.
fam("ambiguous_short", ["wa", "sms", "tg"], "family", ["call me", "hi", "?", "ok", "can you talk", "where r u", "u there?", "listen", "free?", "need to tell you something"], "M", 0, 0, 0, ambiguous=True)
fam("ambiguous_short_friend", ["wa", "sms", "tg"], "friend", ["call me when you see this", "hey", "??", "hmm", "bro", "reply", "sun", "important hai kuch"], "M", 0, 0, 0, ambiguous=True)
fam("link_only", ["wa", "sms", "tg"], "friend", ["{url}", "{url}", "{url}"], "M", 0, 0, 0, ambiguous=True, url="shortener")
fam("link_only_known", ["wa", "tg"], "family", ["{url}", "{url}"], "M", 0, 0, 0, ambiguous=True, url="known")
fam("ambiguous_unknown_number", ["sms", "wa"], ["+91 98{num}", "Unknown", "+91 70{num}"], [
    "Hi, is this you?", "Please call back", "Saw your message, call me", "Regarding your application"], "M", 0, 0, 0, ambiguous=True)

ABBREV = [(r"\byou\b", "u"), (r"\bplease\b", "pls"), (r"\bplease\b", "plz"), (r"\bare\b", "r"), (r"\btomorrow\b", "tmrw"),
          (r"\bmessage\b", "msg"), (r"\btonight\b", "2nite"), (r"\bbecause\b", "coz"), (r"\bminutes\b", "mins"), (r"\bthanks\b", "thx")]


def fill(rng, s):
    def sub(m):
        k = m.group(1)
        if k == "code":
            return str(rng.randint(1000, 999999)).zfill(rng.choice([4, 6]))
        if k == "num":
            return str(rng.randint(10000000, 99999999))
        if k in ("url",):
            return m.group(0)
        if k in SLOTS:
            return rng.choice(SLOTS[k])
        return m.group(0)
    return re.sub(r"\{(\w+)\}", sub, s)


def make_url(rng, kind):
    tpl, safety = rng.choice(URLS[kind])
    r = "".join(rng.choice("abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789") for _ in range(rng.randint(5, 9)))
    url = tpl.replace("{r}", r).replace("{n}", str(rng.randint(0, 254))).replace("{a}", r[:3].lower()).replace("{b}", r[:4].lower()).replace("{c}", r[-3:].lower())
    return url, safety


def noise(rng, s):
    """Informal-writing noise: casing, abbreviations, dropped punctuation, typos, stretched letters."""
    if rng.random() < 0.35:
        for pat, rep in ABBREV:
            if rng.random() < 0.5:
                s = re.sub(pat, rep, s, flags=re.IGNORECASE)
    if rng.random() < 0.3:
        s = s.lower()
    if rng.random() < 0.15:
        s = s.upper() if len(s) < 40 else s
    if rng.random() < 0.25:
        s = re.sub(r"[.,!]", "", s)
    if rng.random() < 0.25:
        words = s.split(" ")
        i = rng.randrange(len(words))
        w = words[i]
        if len(w) > 4 and w.isalpha():
            j = rng.randrange(1, len(w) - 1)
            op = rng.random()
            words[i] = w[:j] + w[j + 1:] if op < 0.5 else (w[:j] + w[j + 1] + w[j] + w[j + 2:] if op < 0.8 else w[:j] + w[j] * 3 + w[j + 1:])
        s = " ".join(words)
    if rng.random() < 0.1:
        s = s + rng.choice([" !!", " 🙏", " 😅", " ...", " ??"])
    return s.strip()


def gold(imp, urg, dis, act, ambiguous):
    """Annotation rules for the derived labels (documented in ml/README.md and mirrored in RiskPolicy.kt)."""
    if ambiguous:
        return "UNKNOWN", "review"
    action = "allow" if (imp == "H" or urg == 1) else "defer"
    if dis == 1 and imp != "H" and urg == 0:
        risk = "HIGH"
    elif dis == 1 or (imp == "M" and act == 1):
        risk = "MEDIUM"
    else:
        risk = "LOW"
    return risk, action


def main():
    rng = random.Random(SEED)
    rows, seen = [], set()
    tid = 0
    for t in T:
        for p in t["patterns"]:
            tid += 1
            made = 0
            for _ in range(VARIANTS_PER_TEMPLATE * 3):
                if made >= VARIANTS_PER_TEMPLATE:
                    break
                app = APPS[rng.choice(t["apps"])]
                sender = t["sender"]
                title = fill(rng, rng.choice(SLOTS[sender]) if isinstance(sender, str) else rng.choice(sender))
                text = fill(rng, p)
                link_safety = "none"
                if "{url}" in text:
                    url, link_safety = make_url(rng, t["url"] or "known")
                    text = text.replace("{url}", url)
                text = noise(rng, text)
                key = (app[0], title.lower(), re.sub(r"\W+", " ", text.lower()).strip())
                if key in seen:
                    continue
                seen.add(key)
                made += 1
                risk, action = gold(t["imp"], t["urg"], t["dis"], t["act"], t["ambiguous"])
                rows.append(dict(
                    template_id=tid, family=t["family"], package=app[0], app_label=app[1], app_category=app[2], title=title, text=text,
                    language=t["lang"], importance_label={"L": "low", "M": "medium", "H": "high"}[t["imp"]],
                    urgency_label="urgent" if t["urg"] else "not_urgent", distraction_label="distracting" if t["dis"] else "not_distracting",
                    action_required=t["act"], contains_link=int(link_safety != "none"), link_safety=link_safety,
                    ambiguous=int(t["ambiguous"]), risk_category=risk, recommended_action=action))
    # Group split by template so near-duplicate variants never leak across splits; stratified by family.
    by_family = {}
    for r in rows:
        by_family.setdefault(r["family"], set()).add(r["template_id"])
    split_of = {}
    for f, tids in sorted(by_family.items()):
        tids = sorted(tids)
        rng.shuffle(tids)
        n = len(tids)
        n_test = max(1, round(n * 0.15)) if n >= 3 else 0
        n_val = max(1, round(n * 0.15)) if n >= 4 else 0
        for i, x in enumerate(tids):
            split_of[x] = "test" if i < n_test else ("validation" if i < n_test + n_val else "train")
    for r in rows:
        r["split"] = split_of[r["template_id"]]
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", newline="", encoding="utf-8") as fh:
        w = csv.DictWriter(fh, fieldnames=list(rows[0].keys()))
        w.writeheader()
        w.writerows(rows)
    counts = {s: sum(1 for r in rows if r["split"] == s) for s in ("train", "validation", "test")}
    print(f"wrote {len(rows)} rows from {tid} templates -> {OUT}  splits={counts}")


if __name__ == "__main__":
    main()
