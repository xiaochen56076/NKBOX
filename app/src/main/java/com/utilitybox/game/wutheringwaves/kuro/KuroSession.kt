package com.utilitybox.game.wutheringwaves.kuro

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 库街区凭据（token）的存放处。
 *
 * ★★ **这是全项目安全等级最高的一份存储。**
 *
 * 它拿到的 token 等于用户库街区账号的访问权 —— 能看绑定角色、能签到、能领库洛币。
 * 因此：
 *
 * 1. **只进应用私有目录**（`MODE_PRIVATE`）。不用外部存储、不导出、不备份
 *    （清单里 `allowBackup="false"` 已经关掉了备份通道）。
 * 2. **绝不出现在日志 / 异常信息 / 界面文本里** —— 本文件没有任何一处打印它。
 * 3. **不写进 `UbSettings`**。那是会被序列化、会被 dump、会在调试时被整体打出来的对象；
 *    凭据单独放，才能保证它不会顺着某个通用路径漏出去。
 *
 * ★ 为什么不用 EncryptedSharedPreferences：
 *   它需要引入 `androidx.security:security-crypto`，而本项目**零新增依赖**是硬约束。
 *   私有目录本身已经受 Android 沙箱保护（非 root 设备上其它应用读不到）。
 *   这个取舍必须写在这里 —— 否则以后有人会以为"忘了加密"。
 */
object KuroCredentialStore {

    private const val FILE = "kuro_auth"
    private const val KEY_TOKEN = "token"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_USER_NAME = "user_name"
    private const val KEY_HEAD_URL = "head_url"
    private const val KEY_DISTINCT_ID = "distinct_id"

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    }

    /**
     * 设备标识。**必须在首次登录前就固定下来** —— 库洛用它做风控，
     * 每次请求换一个新 UUID 会被当成异常设备（甚至直接触发极验）。
     */
    fun distinctId(): String {
        val p = prefs ?: return KuroBbsApi.newDistinctId()
        p.getString(KEY_DISTINCT_ID, null)?.let { return it }
        val fresh = KuroBbsApi.newDistinctId()
        p.edit().putString(KEY_DISTINCT_ID, fresh).apply()
        return fresh
    }

    fun load(): KuroCredentials? {
        val p = prefs ?: return null
        val token = p.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() } ?: return null
        return KuroCredentials(
            token = token,
            userId = p.getString(KEY_USER_ID, "").orEmpty(),
            userName = p.getString(KEY_USER_NAME, "").orEmpty(),
            headUrl = p.getString(KEY_HEAD_URL, null)?.takeIf { it.isNotBlank() },
        )
    }

    fun save(login: LoginResult) {
        prefs?.edit()
            ?.putString(KEY_TOKEN, login.token)
            ?.putString(KEY_USER_ID, login.userId)
            ?.putString(KEY_USER_NAME, login.userName)
            ?.putString(KEY_HEAD_URL, login.headUrl)
            ?.apply()
    }

    fun clear() {
        // 只清凭据，**保留 distinct_id** —— 换设备标识会惊动风控
        prefs?.edit()
            ?.remove(KEY_TOKEN)
            ?.remove(KEY_USER_ID)
            ?.remove(KEY_USER_NAME)
            ?.remove(KEY_HEAD_URL)
            ?.apply()
    }
}

/** 已登录的凭据。[token] 不得进入任何日志或界面文本。 */
data class KuroCredentials(
    val token: String,
    val userId: String,
    val userName: String,
    val headUrl: String?,
)

/**
 * 全局登录态。用可观察状态，任何页面都能跟着变。
 *
 * ★ 与 [KuroCredentialStore] 的分工：本对象是**内存里的可观察镜像**，
 *   存储对象负责落盘。UI 只读这个，不直接碰 SharedPreferences。
 */
object KuroSession {

    var credentials: KuroCredentials? by mutableStateOf(null)
        private set

    /** 当前选中的鸣潮角色；null 表示用账号默认角色 */
    var selectedRole: KuroRole? by mutableStateOf(null)

    fun restore() {
        credentials = KuroCredentialStore.load()
        // 换账号后旧的角色选择必须作废，否则会把 A 账号的 roleId 带给 B 账号
        selectedRole = null
    }

    fun onLoggedIn(login: LoginResult) {
        KuroCredentialStore.save(login)
        credentials = KuroCredentials(login.token, login.userId, login.userName, login.headUrl)
        selectedRole = null
    }

    /** 凭据失效（被其它端顶掉）时的统一出口：清掉并让 UI 回到未登录态 */
    fun onTokenExpired() {
        KuroCredentialStore.clear()
        credentials = null
        selectedRole = null
    }

    fun logout() = onTokenExpired()
}

// ---------------------------------------------------------------------------
// 登录流程
// ---------------------------------------------------------------------------

/** 短信验证码登录的界面状态 */
data class KuroLoginState(
    val mobile: String = "",
    val smsCode: String = "",
    val sending: Boolean = false,
    /** 已发出验证码，等待用户输入 */
    val codeSent: Boolean = false,
    val loggingIn: Boolean = false,
    val error: String? = null,
    /** ★ 库洛要求极验人机验证 —— 短信没发出去。这不是 bug，要如实告诉用户。 */
    val captchaRequired: Boolean = false,
    /** 下次可重发验证码的时间戳（毫秒），0 = 立即可发 */
    val resendAtMillis: Long = 0L,
)

/**
 * 登录流程的驱动器。
 *
 * ★ 手机号与验证码**只存在于内存**（[KuroLoginState]），不落盘、不进日志。
 *   登录成功后只把 token 交给 [KuroSession]。
 */
class KuroLoginController(
    private val scope: CoroutineScope,
    private val onChange: (KuroLoginState) -> Unit,
) {
    var state: KuroLoginState by mutableStateOf(KuroLoginState())
        private set

    private var job: Job? = null

    private fun set(transform: (KuroLoginState) -> KuroLoginState) {
        state = transform(state)
        onChange(state)
    }

    fun onMobileChange(v: String) = set {
        // 只留数字并截到 11 位：手机号里混入空格/横线会让接口报"格式有误"
        it.copy(mobile = v.filter { ch -> ch.isDigit() }.take(11), error = null)
    }

    fun onCodeChange(v: String) = set {
        it.copy(smsCode = v.filter { ch -> ch.isDigit() }.take(6), error = null)
    }

    fun sendCode() {
        val mobile = state.mobile
        if (mobile.length != 11) {
            set { it.copy(error = "手机号应为 11 位") }
            return
        }
        job?.cancel()
        set { it.copy(sending = true, error = null, captchaRequired = false) }
        job = scope.launch {
            when (val r = KuroBbsApi.sendSmsCode(KuroCredentialStore.distinctId(), mobile)) {
                is KuroResult.Ok -> set {
                    if (r.value.needCaptcha) {
                        // ★ 关键分支：接口返回成功，但短信根本没发。
                        //   必须显式告诉用户，否则他会一直等一条不会来的短信。
                        it.copy(
                            sending = false,
                            codeSent = false,
                            captchaRequired = true,
                            error = "库洛要求人机验证，验证码未发出。请稍后重试，或换个网络。",
                        )
                    } else {
                        it.copy(
                            sending = false,
                            codeSent = true,
                            resendAtMillis = System.currentTimeMillis() + 60_000,
                            error = null,
                        )
                    }
                }
                is KuroResult.Failure -> set {
                    it.copy(
                        sending = false,
                        error = when (r.error) {
                            is KuroError.Business -> when ((r.error as KuroError.Business).code) {
                                242 -> "发送过于频繁，请稍后再试"
                                10000 -> "手机号格式有误"
                                else -> r.message
                            }
                            else -> r.message
                        },
                    )
                }
            }
        }
    }

    fun login() {
        val s = state
        if (s.mobile.length != 11) { set { it.copy(error = "手机号应为 11 位") }; return }
        if (s.smsCode.isBlank()) { set { it.copy(error = "请填写验证码") }; return }
        job?.cancel()
        set { it.copy(loggingIn = true, error = null) }
        job = scope.launch {
            when (val r = KuroBbsApi.login(KuroCredentialStore.distinctId(), s.mobile, s.smsCode)) {
                is KuroResult.Ok -> {
                    KuroSession.onLoggedIn(r.value)
                    // ★ 登录成功立刻把手机号与验证码从内存里清掉 —— 它们已经没有用处，
                    //   留着只会多一份泄露面
                    set { KuroLoginState() }
                }
                is KuroResult.Failure -> set {
                    it.copy(
                        loggingIn = false,
                        error = when ((r.error as? KuroError.Business)?.code) {
                            -130 -> "验证码错误或已过期"
                            -10000 -> "手机号或验证码为空"
                            else -> r.message
                        },
                    )
                }
            }
        }
    }
}
