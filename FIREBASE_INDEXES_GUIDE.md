# 🛡️ Preventing the "Month 6 Crash": Firebase Indexing Guide

When our transaction and log history grows over months, NoSQL databases like Firebase can start lagging, freezing, or completely crashing on startup when performing dashboard queries without proper indexes. Following this guide ensures your Weekly Finance application remains blazing fast indefinitely.

---

## ⚡ Option A: Firebase Realtime Database (Dynamic Indexes)
Since our mobile application communicates with the **Firebase Realtime Database (RTD)**, we must define the index rules in the Firebase console.

### How to apply:
1. Go to the [Firebase Console](https://console.firebase.google.com/).
2. Select your project and click on **Realtime Database** in the left menu.
3. Click on the **Rules** tab at the top.
4. Copy the contents of the `firebase-database-rules.json` file in this repository and paste them into the code editor:
   ```json
   {
     "rules": {
       ".read": "auth != null",
       ".write": "auth != null",
       "edit_logs": {
         "$user_node": {
           ".indexOn": ["timestamp", "actionType"]
         }
       },
       "master_databases": {
         "$user_node": {
           ".indexOn": ["timestamp"]
         }
       },
       "dead_letter_queue": {
         "$user_node": {
           ".indexOn": ["timestamp", "flaggedAt"]
         }
       }
     }
   }
   ```
5. Click **Publish** to deploy the rules instantly. This prevents the database from performing expensive linear scans and maintains pre-sorted maps.

---

## 📊 Option B: Firestore compound Indexes (For Laptop Dashboards)
If you built your laptop dashboard utilizing **Cloud Firestore** compound queries (e.g., getting all logs where `type == 'collection'` ordered by `timestamp DESC`), you **must** build composite indexes to prevent Firebase from rejecting the query.

### How to apply:
1. Open the [Firebase Console](https://console.firebase.google.com/).
2. Go to **Firestore Database** in the left sidebar.
3. Click on the **Indexes** tab and select **Composite**.
4. Click **Create Index** or manually deploy indices using the `firestore-indexes.json` file via the Firebase CLI:
   ```bash
   firebase deploy --only firestore:indexes
   ```
5. If creating manually via the console:
   - **Collection ID**: `edit_logs`
   - **Fields**:
     1. `actionType` (Ascending)
     2. `timestamp` (Descending)
   - **Query Scope**: Collection

---

## 📌 Why this is critical
NoSQL engines build dynamic maps matching these definitions. With indices enabled, queries take `O(log N)` complexity instead of `O(N)` database scans, keeping the app startup sync lag-free regardless of historical database size!
