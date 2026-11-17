package main

import (
	"encoding/json"
	"net/http"
	"sort"
	"strings"
	"time"
)

type Message struct {
	ID       string    `json:"id"`
	ConvID   string    `json:"convId"`
	SenderID string    `json:"senderId"`
	Text     string    `json:"text"`
	At       time.Time `json:"at"`
}

// محادثة بين شخصين. المعرف ثابت بغض النظر عن ترتيب الطرفين.
type Conversation struct {
	ID       string    `json:"id"`
	UserA    string    `json:"userA"`
	UserB    string    `json:"userB"`
	Messages []Message `json:"messages"`
	// آخر وقت قرأ فيه كل طرف الرسائل (للعدّاد غير مقروء)
	ReadAt map[string]time.Time `json:"readAt"`
}

func convKey(a, b string) string {
	if a > b {
		a, b = b, a
	}
	return a + "_" + b
}

// authUser يرجّع المستخدم الحالي من رأس Authorization: Bearer <token>
func authUser(r *http.Request) (User, bool) {
	tok := strings.TrimPrefix(r.Header.Get("Authorization"), "Bearer ")
	if tok == "" {
		return User{}, false
	}
	store.mu.Lock()
	defer store.mu.Unlock()
	uid, ok := store.Sessions[tok]
	if !ok {
		return User{}, false
	}
	for _, u := range store.Users {
		if u.ID == uid {
			return u, true
		}
	}
	return User{}, false
}

func requireAuth(h func(w http.ResponseWriter, r *http.Request, me User)) http.HandlerFunc {
	return cors(func(w http.ResponseWriter, r *http.Request) {
		me, ok := authUser(r)
		if !ok {
			fail(w, http.StatusUnauthorized, "", "سجّل الدخول أولاً")
			return
		}
		h(w, r, me)
	})
}

func (s *Store) findUser(id string) (User, bool) {
	for _, u := range s.Users {
		if u.ID == id {
			return u, true
		}
	}
	return User{}, false
}

func (s *Store) findUserByUsername(username string) (User, bool) {
	for _, u := range s.Users {
		if u.Username == username {
			return u, true
		}
	}
	return User{}, false
}

func (s *Store) getOrCreateConv(a, b string) *Conversation {
	key := convKey(a, b)
	if c, ok := s.Convs[key]; ok {
		return c
	}
	c := &Conversation{ID: key, UserA: a, UserB: b, ReadAt: map[string]time.Time{}}
	s.Convs[key] = c
	return c
}

func (c *Conversation) other(me string) string {
	if c.UserA == me {
		return c.UserB
	}
	return c.UserA
}

func (c *Conversation) unreadCount(me string) int {
	last := c.ReadAt[me]
	n := 0
	for _, m := range c.Messages {
		if m.SenderID != me && m.At.After(last) {
			n++
		}
	}
	return n
}

type convOut struct {
	ID          string `json:"id"`
	User        any    `json:"user"`
	LastMessage string `json:"lastMessage"`
	LastAt      string `json:"lastAt"`
	UnreadCount int    `json:"unreadCount"`
}

// GET /api/conversations
func listConversations(w http.ResponseWriter, r *http.Request, me User) {
	store.mu.Lock()
	defer store.mu.Unlock()
	out := []convOut{}
	for _, c := range store.Convs {
		if c.UserA != me.ID && c.UserB != me.ID {
			continue
		}
		other, _ := store.findUser(c.other(me.ID))
		last, lastAt := "", ""
		if len(c.Messages) > 0 {
			m := c.Messages[len(c.Messages)-1]
			last, lastAt = m.Text, m.At.Format(time.RFC3339)
		}
		out = append(out, convOut{ID: c.ID, User: publicUser(other), LastMessage: last, LastAt: lastAt, UnreadCount: c.unreadCount(me.ID)})
	}
	sort.Slice(out, func(i, j int) bool { return out[i].LastAt > out[j].LastAt })
	json.NewEncoder(w).Encode(out)
}

// POST /api/conversations/start {username}
func startConversation(w http.ResponseWriter, r *http.Request, me User) {
	var in struct{ Username string }
	if json.NewDecoder(http.MaxBytesReader(w, r.Body, 1<<16)).Decode(&in) != nil {
		fail(w, 400, "", "طلب غير صالح")
		return
	}
	username := strings.ToLower(strings.TrimSpace(in.Username))
	store.mu.Lock()
	defer store.mu.Unlock()
	other, ok := store.findUserByUsername(username)
	if !ok {
		fail(w, 404, "username", "ما لكينا هذا المستخدم")
		return
	}
	if other.ID == me.ID {
		fail(w, 400, "username", "ما تقدر تراسل نفسك")
		return
	}
	c := store.getOrCreateConv(me.ID, other.ID)
	store.save()
	json.NewEncoder(w).Encode(map[string]any{"id": c.ID, "user": publicUser(other)})
}

// GET /api/conversations/{id}/messages
func listMessages(w http.ResponseWriter, r *http.Request, me User) {
	id := r.PathValue("id")
	store.mu.Lock()
	defer store.mu.Unlock()
	c, ok := store.Convs[id]
	if !ok || (c.UserA != me.ID && c.UserB != me.ID) {
		fail(w, 404, "", "المحادثة غير موجودة")
		return
	}
	c.ReadAt[me.ID] = time.Now()
	store.save()
	json.NewEncoder(w).Encode(c.Messages)
}

// POST /api/conversations/{id}/messages {text}
func sendMessage(w http.ResponseWriter, r *http.Request, me User) {
	id := r.PathValue("id")
	var in struct{ Text string }
	if json.NewDecoder(http.MaxBytesReader(w, r.Body, 4<<10)).Decode(&in) != nil {
		fail(w, 400, "", "طلب غير صالح")
		return
	}
	text := strings.TrimSpace(in.Text)
	if text == "" {
		fail(w, 400, "text", "اكتب رسالة")
		return
	}
	store.mu.Lock()
	defer store.mu.Unlock()
	c, ok := store.Convs[id]
	if !ok || (c.UserA != me.ID && c.UserB != me.ID) {
		fail(w, 404, "", "المحادثة غير موجودة")
		return
	}
	m := Message{ID: randHex(8), ConvID: id, SenderID: me.ID, Text: text, At: time.Now()}
	c.Messages = append(c.Messages, m)
	c.ReadAt[me.ID] = m.At
	store.save()
	json.NewEncoder(w).Encode(m)
}
