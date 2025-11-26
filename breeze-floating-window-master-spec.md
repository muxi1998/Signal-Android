# 🌬️ Breeze AI Floating Assistant – Full Spec (With Resizing + AI Placeholder Integration)
**File:** `breeze-floating-window-master-spec.md`  
**Version:** 2.2  
**Status:** Complete  
**Platform:** Android Overlay (primary), future iOS extension  

---

# 🌟 Core Concept

Breeze AI Floating Assistant follows one philosophy:

> **AI should assist without interrupting.  
> The floating window must be soft, optional, resizable, movable, and gesture-driven.**

To support this, the system:
- Shows the ✨ spark icon only when helpful  
- Opens a floating window on demand  
- Allows multi-turn refinement  
- Adds *slide-to-accept* (↓) and *slide-to-dismiss* (↑)  
- Supports **user resizing + position memory**  
- Uses **AI placeholder mock responses** until backend integration  

This file combines the complete floating window spec + resizing + AI placeholder behavior for prototyping.

---

# 1. 🎯 Purpose

The floating window is a lightweight AI workspace that:
- Refines user text  
- Supports multi-turn AI interactions  
- Avoids obstructing input workflows  
- Allows resizing + moving  
- Saves the user's preferred layout  
- Uses fixed strings to simulate AI until API integration  

---

# 2. ✨ Stage 1 — Input Focus & Spark Icon

### Trigger  
User taps any input field → ✨ spark icon appears.

### Spark Icon Specs
- Size: 18–22dp  
- Color: white @ 80%  
- Animation: soft twinkle (300–500ms)  
- Placement: right side of input field, near send/mic buttons  

Example:
```
[ type your message… ]   ✨
```

Spark icon opens the floating window.

---

# 3. 🌫️ Stage 2 — Floating Window (Full Visual spec)

### Placement  
- Appears **above the input field** by 6–12dp  
- Avoids keyboard and system UI  

### Visual Style  
- Frosted glass (blur 30px)  
- White @ 20–30% opacity  
- Soft orange glow @ 8–12% opacity  
- 20–24dp corner radius  
- Shadow: 0 8px 32px rgba(0,0,0,0.25–0.3)  
- 6–12 subtle drifting light particles  

### Window Structure
```
┌────────────────────────────────────────┐
│ Previous Summary (collapsible)         │
│ Current AI Suggestion                  │
│ Tone / Clarity Chips                   │
│────────────────────────────────────────│
│ Sliding Handle (↑ Reject / ↓ Accept)   │
│ Resize Grip (⤡ bottom-right corner)     │
└────────────────────────────────────────┘
```

---

# 4. 📐 Window Dimensions & Resizing

### Default Size  
- Width: 82–88% screen width  
- Min width: 280dp  
- Max width: screen width − 16dp  
- Height: adaptive  
- Min height: 120dp  
- Max height: 50–55% of screen height  

---

## 4.1 Resizable Window (NEW)

Users resize via a **thick curved resize grip** at bottom-right:

### Resize Grip  
- Shape: curved pill bar  
- Size: 26–32dp width, 10–12dp height  
- Touch area: 32–40dp  
- Opacity: 40–60%  

### Behavior  
- Drag bottom-right → increase size  
- Drag top-left → decrease size  
- Elastic limits  
- Content reflows naturally  

---

## 4.2 Persisted Size & Position (NEW)

Floating window **remembers user preferences**.

### Stored Values  
- Width  
- Height  
- X position  
- Y position  

### When Saved  
- Resize end  
- Move end  

### When Restored  
- Next window open via ✨ spark icon  

### Reset  
- Long press spark icon → “Reset Window Layout”  

---

# 5. 🧊 Window Interactions

### Scrolling  
- Only content scrolls; handle + resize grip remain fixed  

### Tapping  
- Tap summary → expand/collapse  
- Tap chips → trigger new AI refinement  
- Long press suggestion → copy  

### Dragging  
- Drag top area → move window  
- Drag handle → slide accept/reject  
- Drag resize grip → resize  

---

# 6. 🔽 Slide-to-Accept / 🔼 Slide-to-Dismiss

## Downward (↓ Accept & Inject)
- Window follows finger  
- Threshold 40–60px  
- Input field glows orange  
- Inject `Current suggestion` into input field  
- Floating window fades out  
- AI indicator turns on (underline + particles)  

## Upward (↑ Reject)
- Window moves upward  
- Shrinks and fades  
- No injection  
- No indicator  
- Session resets  

---

# 7. 📐 Positioning Logic

### Default  
- Appears above input field  
- 6–12dp spacing  

### After User Adjusts  
- Same location restored on next open  

### Constraints  
- Window must never cover input field  
- Must stay within screen safe zones  
- Must avoid keyboard  

---

# 8. 🎨 Motion & Animation

### Window Pop-in  
- Scale: 0.92 → 1.0 (200ms)  
- Blur: 10px → 30px  
- Opacity fade-in  

### AI Response  
- Fade-in 200ms  
- Previous summary slides down 150ms  

### Resize  
- Elastic interpolation  
- Save state after 100ms debounce  

### Move  
- Soft drag motion  
- Bounds snapping  

---

# 9. 🧪 Edge Cases

- Screen rotation → reflow with preserved ratios  
- Keyboard language switch → avoid jitter  
- Very small screens → enforce min size  
- Backgrounding app → close window  

---

# 10. 🧘 UX Principles

- Window is user-controlled  
- No forced CTAs  
- Gesture-driven decisions  
- Resize + memory → personalization  
- Floating UI must feel soft & premium  
- Placeholder AI must behave like real AI logically  

---

# 11. 🧠 AI Interaction — Placeholder Mode (NEW)

Before real AI backend is integrated, all AI responses use **fixed mock strings**.

This enables UI/UX development to proceed without any backend.

---

## 11.1 AI Invocation Model

In placeholder mode:

- No API calls  
- Every refinement returns a predefined string  
- Multi-turn behavior simulated with mock text  
- UI behavior identical to real AI integration  

Later, simply replace:  
`onAIResponseReceived(string text)`  
with real API callback.

---

## 11.2 Placeholder Outputs (Recommended)

### Initial Refinement  
`"This is the AI-refined version of your text."`

### Tone Modifiers  
Formal:  
`"[Formal] Your message has been rewritten professionally."`  

Friendly:  
`"[Friendly] Here's a more casual, warm version!"`  

Shorter:  
`"[Shortened] Here is a more concise rewrite."`  

Expanded:  
`"[Expanded] I've added more detail to your message."`  

### Multi-Turn Responses  
Turn 1:  
`"Here's my first suggestion based on your input!"`  

Turn 2:  
`"Here's an improved version after your refinement request."`  

Turn 3:  
`"Polishing further—here’s an even cleaner version."`  

### Error Mock  
`"[AI Error Placeholder] Could not generate text."`

---

## 11.3 Placeholder Multi-Turn Sample

User input:  
`"I will be late to the meeting."`

AI Turn 1:  
`"I may arrive slightly later than scheduled. Thank you for your patience!"`

Formal refine:  
`"[Formal] I would like to inform you that I may arrive later than scheduled."`

Shorten refine:  
`"[Shortened] I may be late. Thank you."`

Friendly refine:  
`"[Friendly] Hey! Just a heads-up—I might be a little late 😅"`  

---

## 11.4 AI Placeholder State Flow

### 1. User taps ✨  
→ Window opens → `AI_IDLE`

### 2. System generates placeholder response  
→ `AI_SUGGESTING`

### 3. Current suggestion updated  
→ Previous summary updated  
→ Window rerenders

### 4. User taps chip  
→ Use chip-specific placeholder string  
→ Move old text to summary

### 5. User chooses:  
↓ Slide down → Inject  
↑ Slide up → Dismiss  

---

## 11.5 Placeholder Data Table

| Action | Mock Output |
|--------|-------------|
| initial refine | `"AI refined your text."` |
| formal | `"[Formal] AI rewrote your message formally."` |
| friendly | `"[Friendly] Here's a friendlier version."` |
| clarity | `"[Clearer] Improved clarity."` |
| shorten | `"[Shortened] Condensed version."` |
| expand | `"[Expanded] Added detail."` |
| turn 2 | `"Here's an improved version after your adjustment."` |
| error | `"[AI Error Placeholder]"` |

---

# 12. 🔗 Migration to Real AI API

Easy replacement:

```
POST /v1/refine
{
  "text": "<input>",
  "style": "<formal/friendly/etc>"
}

→ response.text → Current suggestion
```

No UI changes required.

---

# End of Master Spec