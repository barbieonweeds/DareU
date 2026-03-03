package com.amanabha.dareu

import android.os.Bundle
import android.os.CountDownTimer
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    companion object {
        init { System.loadLibrary("dareu") }
        const val SERVER_URL = "wss://YOUR-APP-NAME.onrender.com"
    }

    private external fun setupGame(n1: String, n2: String, w1: String, w2: String)
    private external fun getRemainingTime(): String
    private external fun getRemainingSeconds(): Int
    private external fun getCurrentPlayerName(): String
    private external fun submitMessage(msg: String): String

    private var ws: WebSocket? = null
    private var myIndex = 0
    private var myName = ""
    private var myWins = 0
    private var myLosses = 0
    private var roomId = ""
    private var timer: CountDownTimer? = null

    // Views
    private lateinit var timerText: TextView
    private lateinit var chatLog: LinearLayout
    private lateinit var inputField: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var turnLabel: TextView
    private lateinit var oppNameText: TextView
    private lateinit var oppAvatarText: TextView
    private lateinit var typingIndicator: TextView
    private lateinit var myWinsText: TextView
    private lateinit var myLossesText: TextView
    private lateinit var scrollView: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showHomeScreen()
    }

    private fun showHomeScreen() {
        setContentView(R.layout.activity_home)
        val nameInput = findViewById<EditText>(R.id.nameInput)
        val randomBtn = findViewById<Button>(R.id.randomBtn)
        val createBtn = findViewById<Button>(R.id.createRoomBtn)
        val joinBtn   = findViewById<Button>(R.id.joinRoomBtn)
        val winsTV    = findViewById<TextView>(R.id.homeWins)
        val lossesTV  = findViewById<TextView>(R.id.homeLosses)

        winsTV.text   = "Wins: $myWins"
        lossesTV.text = "Losses: $myLosses"

        randomBtn.setOnClickListener {
            myName = nameInput.text.toString().trim().ifEmpty { "Player" }
            connectAndSend(JSONObject().apply {
                put("type", "JOIN_RANDOM")
                put("name", myName)
                put("wins", myWins)
                put("losses", myLosses)
                put("avatar", myName.first().uppercase())
            })
            showWaitingScreen("Finding a match...")
        }

        createBtn.setOnClickListener {
            myName = nameInput.text.toString().trim().ifEmpty { "Player" }
            val code = (100000..999999).random().toString().substring(0,6)
            connectAndSend(JSONObject().apply {
                put("type", "CREATE_ROOM")
                put("name", myName)
                put("wins", myWins)
                put("losses", myLosses)
                put("avatar", myName.first().uppercase())
                put("roomId", code)
            })
            showWaitingScreen("Room Code: $code\nShare with your friend!")
        }

        joinBtn.setOnClickListener {
            myName = nameInput.text.toString().trim().ifEmpty { "Player" }
            showJoinRoomDialog()
        }
    }

    private fun showJoinRoomDialog() {
        setContentView(R.layout.activity_join_room)
        val codeInput = findViewById<EditText>(R.id.roomCodeInput)
        val joinBtn   = findViewById<Button>(R.id.confirmJoinBtn)
        val backBtn   = findViewById<Button>(R.id.backBtn)

        joinBtn.setOnClickListener {
            val code = codeInput.text.toString().trim().uppercase()
            if (code.length != 6) {
                Toast.makeText(this, "Enter a valid 6-digit code", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            connectAndSend(JSONObject().apply {
                put("type", "JOIN_ROOM")
                put("name", myName)
                put("wins", myWins)
                put("losses", myLosses)
                put("avatar", myName.first().uppercase())
                put("roomId", code)
            })
            showWaitingScreen("Joining room $code...")
        }
        backBtn.setOnClickListener { showHomeScreen() }
    }

    private fun showWaitingScreen(message: String) {
        setContentView(R.layout.activity_waiting)
        val msgText = findViewById<TextView>(R.id.waitingMessage)
        val backBtn = findViewById<Button>(R.id.cancelBtn)
        msgText.text = message
        backBtn.setOnClickListener { ws?.close(1000, "cancelled"); showHomeScreen() }
    }

    private fun showWordScreen(opponentName: String) {
        runOnUiThread {
            setContentView(R.layout.activity_word)
            val wordInput = findViewById<EditText>(R.id.wordInput)
            val confirmBtn = findViewById<Button>(R.id.confirmWordBtn)
            val oppText = findViewById<TextView>(R.id.versusText)
            oppText.text = "$myName  vs  $opponentName"

            confirmBtn.setOnClickListener {
                val word = wordInput.text.toString().trim().lowercase()
                if (word.isEmpty() || word.contains(" ")) {
                    Toast.makeText(this, "Enter a single word!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                send(JSONObject().apply { put("type", "SET_WORD"); put("word", word) })
                confirmBtn.isEnabled = false
                confirmBtn.text = "Waiting for opponent..."
            }
        }
    }

    private fun showGameScreen(opponentName: String, opponentAvatar: String,
                               opponentWins: Int, opponentLosses: Int) {
        runOnUiThread {
            setContentView(R.layout.activity_game)
            timerText       = findViewById(R.id.timerText)
            chatLog         = findViewById(R.id.chatLog)
            inputField      = findViewById(R.id.inputField)
            sendButton      = findViewById(R.id.sendButton)
            turnLabel       = findViewById(R.id.myNameText)
            oppNameText     = findViewById(R.id.oppNameText)
            oppAvatarText   = findViewById(R.id.oppAvatar)
            typingIndicator = findViewById(R.id.typingIndicator)
            myWinsText      = findViewById(R.id.myWins)
            myLossesText    = findViewById(R.id.myLosses)
            scrollView      = findViewById(R.id.scrollView)

            turnLabel.text      = myName
            oppNameText.text    = opponentName
            oppAvatarText.text  = opponentAvatar
            myWinsText.text     = "W: $myWins"
            myLossesText.text   = "L: $myLosses"
            timerText.text      = "07:00"

            startLocalTimer()

            var typingTimer: CountDownTimer? = null
            inputField.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {
                    send(JSONObject().apply { put("type", "TYPING"); put("isTyping", true) })
                    typingTimer?.cancel()
                    typingTimer = object : CountDownTimer(1500, 1500) {
                        override fun onTick(m: Long) {}
                        override fun onFinish() {
                            send(JSONObject().apply { put("type", "TYPING"); put("isTyping", false) })
                        }
                    }.start()
                }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })

            sendButton.setOnClickListener { sendChat() }
            inputField.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEND) { sendChat(); true } else false
            }
        }
    }

    private fun sendChat() {
        val text = inputField.text.toString().trim()
        if (text.isEmpty()) return
        send(JSONObject().apply { put("type", "CHAT"); put("message", text) })
        inputField.text.clear()
    }

    private fun addChatBubble(message: String, senderName: String, isMine: Boolean) {
        runOnUiThread {
            val inflater = layoutInflater
            val bubbleView = inflater.inflate(
                if (isMine) R.layout.bubble_mine else R.layout.bubble_theirs,
                chatLog, false
            )
            bubbleView.findViewById<TextView>(R.id.bubbleText).text = message
            bubbleView.findViewById<TextView>(R.id.bubbleSender).text = senderName
            chatLog.addView(bubbleView)
            scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }

    private fun startLocalTimer() {
        timer?.cancel()
        timer = object : CountDownTimer(420000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val s = (millisUntilFinished / 1000).toInt()
                val m = s / 60
                val sec = s % 60
                val timeStr = "${if(m<10)"0$m" else "$m"}:${if(sec<10)"0$sec" else "$sec"}"
                runOnUiThread { timerText.text = timeStr }
                if (s == 60) addChatBubble("⏰ One minute left!", "System", false)
                if (s == 30) addChatBubble("⏰ 30 seconds!", "System", false)
            }
            override fun onFinish() {
                runOnUiThread { timerText.text = "00:00" }
            }
        }.start()
    }

    private fun showEndScreen(won: Boolean, trapWord: String, loserName: String) {
        timer?.cancel()
        runOnUiThread {
            setContentView(R.layout.activity_end)
            val resultText  = findViewById<TextView>(R.id.resultText)
            val trapWordTV  = findViewById<TextView>(R.id.trapWordText)
            val loserTV     = findViewById<TextView>(R.id.loserText)
            val playAgainBtn= findViewById<Button>(R.id.playAgainBtn)
            val homeBtn     = findViewById<Button>(R.id.homeBtn)

            if (won) { myWins++; resultText.text = "🎉 YOU WIN!" }
            else     { myLosses++; resultText.text = "💀 YOU LOSE" }

            trapWordTV.text = "Trap word: \"$trapWord\""
            loserTV.text    = "$loserName said it!"

            playAgainBtn.setOnClickListener { showHomeScreen() }
            homeBtn.setOnClickListener { showHomeScreen() }
        }
    }

    // ── WebSocket ─────────────────────────────────────────────────────────

    private fun connectAndSend(initialMsg: JSONObject) {
        val client = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
        val request = Request.Builder().url(SERVER_URL).build()
        var pendingMsg = initialMsg

        ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(pendingMsg.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val msg = JSONObject(text)
                handleServerMessage(msg)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Connection failed: ${t.message}", Toast.LENGTH_LONG).show()
                    showHomeScreen()
                }
            }
        })
    }

    private fun send(data: JSONObject) { ws?.send(data.toString()) }

    private fun handleServerMessage(msg: JSONObject) {
        when (msg.getString("type")) {
            "WAITING" -> { /* already showing waiting screen */ }

            "ROOM_CREATED" -> {
                roomId = msg.getString("roomId")
                runOnUiThread {
                    val msgTV = findViewById<TextView?>(R.id.waitingMessage)
                    msgTV?.text = "Room Code: $roomId\nShare with your friend!"
                }
            }

            "MATCHED" -> {
                myIndex = msg.getInt("yourIndex")
                val opp = msg.getJSONObject("opponent")
                showWordScreen(opp.getString("name"))
                runOnUiThread {
                    this.roomId = msg.optString("roomId", roomId)
                }
                val oppName   = opp.getString("name")
                val oppAvatar = opp.getString("avatar")
                val oppWins   = opp.getInt("wins")
                val oppLosses = opp.getInt("losses")
                this.pendingOppName   = oppName
                this.pendingOppAvatar = oppAvatar
                this.pendingOppWins   = oppWins
                this.pendingOppLosses = oppLosses
            }

            "WORD_SET"   -> { /* waiting for opponent word */ }

            "GAME_START" -> {
                showGameScreen(pendingOppName, pendingOppAvatar, pendingOppWins, pendingOppLosses)
            }

            "CHAT" -> {
                val message     = msg.getString("message")
                val senderIndex = msg.getInt("senderIndex")
                val senderName  = msg.getString("senderName")
                addChatBubble(message, senderName, senderIndex == myIndex)
            }

            "TYPING" -> {
                runOnUiThread {
                    typingIndicator.visibility =
                        if (msg.getBoolean("isTyping")) android.view.View.VISIBLE
                        else android.view.View.GONE
                }
            }

            "WIN"  -> showEndScreen(true,  msg.getString("trapWord"), msg.getString("loserName"))
            "LOSE" -> showEndScreen(false, msg.getString("trapWord"), msg.getString("loserName"))
            "DRAW" -> showEndScreen(false, "none", "nobody")

            "OPPONENT_LEFT" -> {
                timer?.cancel()
                runOnUiThread {
                    Toast.makeText(this, "Opponent disconnected!", Toast.LENGTH_LONG).show()
                    showHomeScreen()
                }
            }

            "ERROR" -> {
                runOnUiThread {
                    Toast.makeText(this, msg.getString("message"), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Temp opponent info storage
    private var pendingOppName   = ""
    private var pendingOppAvatar = "?"
    private var pendingOppWins   = 0
    private var pendingOppLosses = 0

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
        ws?.close(1000, "app closed")
    }
}