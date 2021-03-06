package com.foodduck.foodduck.account.service

import com.foodduck.foodduck.account.dto.*
import com.foodduck.foodduck.account.model.Account
import com.foodduck.foodduck.account.repository.AccountRepository
import com.foodduck.foodduck.account.repository.ReasonRepository
import com.foodduck.foodduck.base.config.S3Uploader
import com.foodduck.foodduck.base.config.domain.EntityFactory
import com.foodduck.foodduck.base.config.security.jwt.JwtProvider
import com.foodduck.foodduck.base.config.security.token.TokenDto
import com.foodduck.foodduck.base.error.CustomException
import com.foodduck.foodduck.base.error.ErrorCode
import com.foodduck.foodduck.base.message.ACCOUNT_PROFILE_DIR_NAME
import com.foodduck.foodduck.base.message.PrefixType
import com.foodduck.foodduck.base.util.FoodDuckUtil
import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.repository.findByIdOrNull
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.util.ReflectionTestUtils
import java.io.FileInputStream
import java.time.Duration
import java.util.*
import javax.servlet.http.HttpServletRequest

internal class AccountServiceTest {

    private lateinit var accountService: AccountService

    @MockK
    private lateinit var accountRepository: AccountRepository

    @MockK
    private lateinit var jwtProvider: JwtProvider

    @MockK
    private lateinit var passwordEncoder: PasswordEncoder

    @MockK
    private lateinit var redisTemplate: RedisTemplate<String, String>

    @RelaxedMockK
    private lateinit var javaMailSender: JavaMailSender

    @MockK
    private lateinit var reasonRepository: ReasonRepository

    @MockK
    private lateinit var s3Uploader: S3Uploader

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        mockkStatic(FoodDuckUtil::class)
        mockkObject(FoodDuckUtil)
        every { FoodDuckUtil.authenticationNumber() }.returns("12345")
        accountService = AccountService(
            accountRepository,
            jwtProvider,
            passwordEncoder,
            redisTemplate,
            javaMailSender,
            reasonRepository,
            s3Uploader
        )
    }

    @AfterEach
    fun tearDown() {
        clearStaticMockk(FoodDuckUtil::class)
    }

    @Test
    fun `???????????? ??????`() {

        val email = "foodduck@example.com"
        val password = "Test12#$"
        val encodePassword = "\$2a\$10\$Y2C2wVyIh5inOWStOe6sNOv4ggk50vOHsP6ZPDwW07YBGpW0i5WHO"
        val nickname = "foodduck"
        val request =
            AccountSignUpRequest(email = email, nickname = nickname, password = password, checkPassword = "Test12#$")
        val account = Account(email = email, password = encodePassword, nickname = nickname, profile = "")
        val token = TokenDto("accessToken", "refreshToken")

        every { accountRepository.existsByEmail(email) } returns false
        every { accountRepository.existsByNickname(nickname) } returns false
        every { passwordEncoder.encode(password) } returns encodePassword
        every { accountRepository.save(any()) } returns account
        every { jwtProvider.createAllToken(email, Collections.singletonList("ROLE_USER")) } returns token

        val result = accountService.signUp(request)
        assertThat(result).isEqualTo(token)
    }

    @Test
    fun `???????????? ????????? ?????? ??????`() {
        val email = "foodduck@example"
        val password = "Test12#$"
        val nickname = "foodduck"
        val request =
            AccountSignUpRequest(email = email, nickname = nickname, password = password, checkPassword = "Test12#$")

        assertThrows(CustomException::class.java) {
            accountService.signUp(request)
        }
    }

    @Test
    fun `???????????? ???????????? ?????? ??????`() {
        val email = "foodduck@example.com"
        val password = "Test12"
        val checkPassword = "Test12"
        val nickname = "foodduck"
        val request =
            AccountSignUpRequest(email = email, nickname = nickname, password = password, checkPassword = checkPassword)

        every { accountRepository.existsByEmail(email) } returns false

        assertThrows(CustomException::class.java) {
            accountService.signUp(request)
        }
    }

    @Test
    fun `???????????? 2??? ???????????? ????????????`() {
        val email = "foodduck@example.com"
        val password = "Test12#$"
        val checkPassword = "Test12"
        val nickname = "foodduck"
        val request =
            AccountSignUpRequest(email = email, nickname = nickname, password = password, checkPassword = checkPassword)

        every { accountRepository.existsByEmail(email) } returns false

        assertThrows(CustomException::class.java) {
            accountService.signUp(request)
        }
    }

    @Test
    fun `???????????? ????????? ?????? ?????????`() {
        val email = "foodduck@example.com"
        val password = "Test12#$"
        val checkPassword = "Test12#$"
        val nickname = "foodduck"
        val request =
            AccountSignUpRequest(email = email, nickname = nickname, password = password, checkPassword = checkPassword)

        every { accountRepository.existsByEmail(email) } returns false
        every { accountRepository.existsByNickname(nickname) } returns true

        assertThrows(CustomException::class.java) {
            accountService.signUp(request)
        }
    }

    @Test
    fun `???????????? ???????????? 2??? ???????????? ?????????`() {
        val email = "foodduck@example.com"
        val password = "Test12#$"
        val checkPassword = "Test12#4"
        val nickname = "foodduck"
        val request =
            AccountSignUpRequest(email = email, nickname = nickname, password = password, checkPassword = checkPassword)

        every { accountRepository.existsByEmail(email) } returns false
        every { accountRepository.existsByNickname(nickname) } returns true

        assertThrows(CustomException::class.java) {
            accountService.signUp(request)
        }
    }

    @Test
    fun `????????? ??????`() {
        val nickname = "foodduck"

        every { accountRepository.existsByNickname(nickname) } returns false

        assertDoesNotThrow {
            accountService.checkNickname(nickname)
        }
    }

    @Test
    fun `????????? ?????? ???????????? ???`() {
        val nickname = "foodduck"

        every { accountRepository.existsByNickname(nickname) } returns true

        assertThrows(CustomException::class.java) {
            accountService.checkNickname(nickname)
        }
    }

    @Test
    fun `????????? ??????`() {
        val email = "foodduck@example.com"
        val password = "Test12#$"
        val nickname = "foodduck"
        val encodePassword = "\$2a\$10\$Y2C2wVyIh5inOWStOe6sNOv4ggk50vOHsP6ZPDwW07YBGpW0i5WHO"
        val request = AccountLoginRequest(email = email, password = password)
        val account = Account(email = email, password = encodePassword, nickname = nickname, profile = "")
        val token = TokenDto("accessToken", "refreshToken")


        every { accountRepository.findByEmail(email) } returns account
        every { passwordEncoder.encode(password) } returns encodePassword
        every { passwordEncoder.matches(password, encodePassword) } returns true
        every { jwtProvider.createAllToken(email, Collections.singletonList("ROLE_USER")) } returns token
        every { jwtProvider.saveRefreshToken(email, "refreshToken") } returnsArgument 0

        val result = accountService.login(request)
        assertThat(result).isEqualTo(token)
    }

    @Test
    fun `????????? ?????? ?????? ???????????? ???????????????`() {
        val email = "foodduck@example.com"
        val password = "Test12#$"
        val request = AccountLoginRequest(email = email, password = password)

        every { accountRepository.findByEmail(email) } throws CustomException(ErrorCode.USER_NOT_FOUND_ERROR)
        assertThrows(CustomException::class.java) {
            accountService.login(request)
        }
    }

    @Test
    fun `????????? ?????? ??????????????? ?????????`() {
        val email = "foodduck@example.com"
        val password = "Test12#$"
        val nickname = "foodduck"
        val encodePassword = "\$2a\$10\$Y2C2wVyIh5inOWStOe6sNOv4ggk50vOHsP6ZPDwW07YBGpW0i5WHO"
        val request = AccountLoginRequest(email = email, password = password)
        val account = Account(email = email, password = encodePassword, nickname = nickname, profile = "")


        every { accountRepository.findByEmail(email) } returns account
        every { passwordEncoder.encode(password) } returns encodePassword
        every { passwordEncoder.matches(password, encodePassword) } returns false

        assertThrows(CustomException::class.java) {
            accountService.login(request)
        }
    }

    @Test
    fun `accessToken ??? ??????????????? ??? refreshToken ?????? ?????? ?????? accessToken ??? refreshToken ??? ??????`() {
        val email = "foodduck@example.com"
        val refreshToken = "refreshToken===="
        val tokenDto = TokenDto("newAccessToken", "newRefreshToken")
        every { jwtProvider.checkRefreshToken(email, refreshToken) } returnsArgument 0
        every { jwtProvider.reIssueAllToken(email, Collections.singletonList("ROLE_USER")) } returns tokenDto

        val result = accountService.reIssueToken(email, refreshToken)
        assertThat(result).isEqualTo(tokenDto)
    }

    @Test
    fun `?????? ?????? ???????????? ?????????`() {
        ReflectionTestUtils.setField(accountService, "sendFrom", "foodduck@duck.co.kr")

        val email = "foodduck@example.com"
        val key = PrefixType.TEMP_PASSWORD.prefix + email
        val number = "12345"

        val simpleMailMessage = SimpleMailMessage()
        simpleMailMessage.setTo()
        simpleMailMessage.setFrom(email)
        simpleMailMessage.setSubject("Food Duck ????????????")
        simpleMailMessage.setText(number)

        every { accountRepository.existsByEmail(email) }.returns(true)
        every { javaMailSender.send(simpleMailMessage) } returnsArgument 0
        every { redisTemplate.opsForValue().set(key, number) }.returnsArgument(0)
        every { redisTemplate.expire(key, Duration.ofMinutes(FoodDuckUtil.AUTHENTICATE_DURATION_MINUTE)) }.returns(true)

        assertDoesNotThrow {
            accountService.sendTempAuthenticateNumber(email)
        }
    }

    @Test
    fun `????????????`() {
        val email = "foodduck@example.com"
        val encodePassword = "\$2a\$10\$Y2C2wVyIh5inOWStOe6sNOv4ggk50vOHsP6ZPDwW07YBGpW0i5WHO"
        val nickname = "foodduck"
        val account = Account(email = email, password = encodePassword, nickname = nickname, profile = "")
        val request = mockk<HttpServletRequest>()

        every { jwtProvider.logout(request, email) }.returnsArgument(0)

        assertDoesNotThrow {
            accountService.logout(account, request)
        }
    }

    @Test
    fun `???????????? ????????????`() {
        val email = "foodduck@example.com"
        val number = "12345"
        val key = PrefixType.TEMP_PASSWORD.prefix + email
        every { redisTemplate.opsForValue().get(key) }.returns(number)
        every { redisTemplate.delete(key) }.returns(true)

        assertDoesNotThrow {
            accountService.compareAuthenticateNumber(email, number)
        }
    }

    @Test
    fun `???????????? ????????? ???`() {
        val email = "foodduck@example.com"
        val number = "12345"
        val key = PrefixType.TEMP_PASSWORD.prefix + email
        every { redisTemplate.opsForValue().get(key) }.returns("54321")

        assertThrows(CustomException::class.java) {
            accountService.compareAuthenticateNumber(email, number)
        }
    }

    @Test
    fun `???????????? ??????`() {
        val email = "fodduck@example.com"
        val password = "Test12#$"
        val checkPassword = "Test12#$"
        val encodePassword = "\$2a\$10\$Y2C2wVyIh5inOWStOe6sNOv4ggk50vOHsP6ZPDwW07YBGpW0i5WHO"
        val nickname = "foodduck"
        val account = Account(email = email, password = encodePassword, nickname = nickname, profile = "")
        val request = AccountChangePasswordRequest(password = password, checkPassword = checkPassword)

        every { accountRepository.findByEmail(email) }.returns(account)
        every { passwordEncoder.encode(password) }.returns(encodePassword)

        assertDoesNotThrow {
            accountService.changePassword(email, request)
        }
    }

    @Test
    fun `???????????? ?????? ????????????1`() {
        val email = "fodduck@example.com"
        val password = "Test12"
        val checkPassword = "Test12#$"
        val request = AccountChangePasswordRequest(password = password, checkPassword = checkPassword)
        assertThrows(CustomException::class.java) {
            accountService.changePassword(email, request)
        }
    }

    @Test
    fun `???????????? ?????? ????????????2`() {
        val email = "fodduck@example.com"
        val password = "Test12#$"
        val checkPassword = "Test12"
        val request = AccountChangePasswordRequest(password = password, checkPassword = checkPassword)
        assertThrows(CustomException::class.java) {
            accountService.changePassword(email, request)
        }
    }

    @Test
    fun `???????????? ?????? 1??? 2??? ???????????? ?????????`() {
        val email = "fodduck@example.com"
        val password = "Test12#$"
        val checkPassword = "Test12#1"
        val request = AccountChangePasswordRequest(password = password, checkPassword = checkPassword)
        assertThrows(CustomException::class.java) {
            accountService.changePassword(email, request)
        }
    }

    @Test
    fun `????????? ???????????? ????????????`() {
        val email = "fodduck@example.com"
        val password = "Test12#$"
        val checkPassword = "Test12#1"
        val request = AccountChangePasswordRequest(password = password, checkPassword = checkPassword)

        every { accountRepository.findByEmail(email) }.throws(CustomException(ErrorCode.USER_NOT_FOUND_ERROR))

        assertThrows(CustomException::class.java) {
            accountService.changePassword(email, request)
        }
    }

    @Test
    fun `?????? ??????`() {
        val email = "fodduck@example.com"
        val encodePassword = "\$2a\$10\$Y2C2wVyIh5inOWStOe6sNOv4ggk50vOHsP6ZPDwW07YBGpW0i5WHO"
        val nickname = "foodduck"
        val account = Account(email = email, password = encodePassword, nickname = nickname, profile = "")
        val request = SignOutRequest(reason = "simple")
        every { reasonRepository.save(any()) }.returnsArgument(0)
        assertDoesNotThrow {
            accountService.signOut(account, request)
        }
    }

    @Test
    fun `????????? ??? ???????????? ??????`() {
        val email = "fodduck@example.com"
        val password = "Test12#$"
        val encodePassword = "\$2a\$10\$Y2C2wVyIh5inOWStOe6sNOv4ggk50vOHsP6ZPDwW07YBGpW0i5WHO"
        val nickname = "foodduck"
        val account = Account(email = email, password = encodePassword, nickname = nickname, profile = "")
        val changePassword = "myPass12#$"
        val changePassword2 = "myPass12#$"
        val request = LoginAccountChangePasswordRequest(password, changePassword, changePassword2)

        every { passwordEncoder.matches(password, encodePassword) }.returns(true)
        every { accountRepository.findByIdOrNull(account.id) }.returns(account)
        every { passwordEncoder.encode(changePassword) }.returnsArgument(0)

        assertDoesNotThrow {
            accountService.loginChangePassword(account, request)
        }
    }

    @Test
    fun `????????? ????????? ?????????`() {
        val account = EntityFactory.accountTemplate()
        val fis = FileInputStream("src/test/resources/static/test.png")
        val image = MockMultipartFile("file", fis)
        val imageUrl = ACCOUNT_PROFILE_DIR_NAME + "local" + "image.png"
        every { accountRepository.findByIdOrNull(id=account.id) }.returns(account)
        every { s3Uploader.upload(any(), any()) }.returns(imageUrl)
        assertDoesNotThrow {
            accountService.updateProfile(account, image)
        }
        assertThat(account.profile).isEqualTo(imageUrl)
    }

}