package main

import (
	"encoding/json"
	"log"
	"net/http"
	"strconv"
	"sync"
)

type Video struct {
	ID     int    `json:"id"`
	Title  string `json:"title"`
	Author string `json:"author"`
	URL    string `json:"url"`
	Likes  int    `json:"likes"`
}

var (
	mu     sync.Mutex
	videos = []Video{
		{1, "أول فيديو", "أحمد", "https://flutter.github.io/assets-for-api-docs/assets/videos/bee.mp4", 0},
		{2, "فيديو ثاني", "سارة", "https://flutter.github.io/assets-for-api-docs/assets/videos/butterfly.mp4", 0},
	}
)

func cors(h http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Access-Control-Allow-Origin", "*")
		w.Header().Set("Content-Type", "application/json; charset=utf-8")
		h(w, r)
	}
}

func main() {
	store.load()
	http.Handle("GET /uploads/", http.StripPrefix("/uploads/", http.FileServer(http.Dir("uploads"))))
	http.HandleFunc("GET /api/auth/username-available", cors(usernameAvailable))
	http.HandleFunc("POST /api/auth/register", cors(register))
	http.HandleFunc("POST /api/auth/login", cors(login))
	http.HandleFunc("POST /api/auth/forgot", cors(forgot))
	http.HandleFunc("GET /api/conversations", requireAuth(listConversations))
	http.HandleFunc("POST /api/conversations/start", requireAuth(startConversation))
	http.HandleFunc("GET /api/conversations/{id}/messages", requireAuth(listMessages))
	http.HandleFunc("POST /api/conversations/{id}/messages", requireAuth(sendMessage))
	http.HandleFunc("GET /api/videos", cors(func(w http.ResponseWriter, r *http.Request) {
		mu.Lock()
		defer mu.Unlock()
		json.NewEncoder(w).Encode(videos)
	}))
	http.HandleFunc("POST /api/videos/{id}/like", cors(func(w http.ResponseWriter, r *http.Request) {
		id, err := strconv.Atoi(r.PathValue("id"))
		mu.Lock()
		defer mu.Unlock()
		for i := range videos {
			if err == nil && videos[i].ID == id {
				videos[i].Likes++
				json.NewEncoder(w).Encode(videos[i])
				return
			}
		}
		http.Error(w, `{"error":"not found"}`, http.StatusNotFound)
	}))
	log.Println("جماعتكم server on :8080")
	log.Fatal(http.ListenAndServe(":8080", nil))
}
