package com.example.course_schedule_for_chd_v002.ui.screens.login

import com.example.course_schedule_for_chd_v002.domain.repository.ICourseRepository
import com.example.course_schedule_for_chd_v002.domain.repository.LoginResult
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * LoginViewModel 单元测试
 * 遵循 TDD 开发规范，测试先行
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var mockRepository: ICourseRepository
    private lateinit var viewModel: LoginViewModel

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        mockRepository = mockk()
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    // ================ 登录测试 ================

    @Test
    fun `login with validCredentials updates state to success`() = runTest {
        // Given
        coEvery { mockRepository.login(any(), any()) } returns Result.success(
            LoginResult(success = true, studentName = "Zhang San", studentId = "20240001")
        )
        coEvery { mockRepository.fetchRemoteSchedule(any()) } returns Result.success(emptyList())

        viewModel = LoginViewModel(mockRepository)
        viewModel.onUsernameChange("20240001")
        viewModel.onPasswordChange("password")

        // When
        viewModel.login()
        advanceUntilIdle()

        // Then
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.isLoggedIn)
        assertEquals("Zhang San", state.studentName)
    }

    @Test
    fun `login with emptyUsername shows error`() = runTest {
        // Given
        viewModel = LoginViewModel(mockRepository)

        viewModel.onPasswordChange("password")

        // When
        viewModel.login()
        advanceUntilIdle()

        // Then
        val state = viewModel.uiState.value
        assertNotNull(state.usernameError)
        assertFalse(state.isLoading)
    }

    @Test
    fun `login with emptyPassword shows error`() = runTest {
        // Given
        viewModel = LoginViewModel(mockRepository)

        viewModel.onUsernameChange("20240001")

        // When
        viewModel.login()
        advanceUntilIdle()

        // Then
        val state = viewModel.uiState.value
        assertNotNull(state.passwordError)
    }

    @Test
    fun `login with invalidCredentials shows errorMessage`() = runTest {
        // Given
        coEvery { mockRepository.login(any(), any()) } returns Result.success(
            LoginResult(success = false, errorMessage = "Wrong password")
        )

        viewModel = LoginViewModel(mockRepository)
        viewModel.onUsernameChange("20240001")
        viewModel.onPasswordChange("wrong")

        // When
        viewModel.login()
        advanceUntilIdle()

        // Then
        val state = viewModel.uiState.value
        assertTrue(state.errorMessage?.contains("Wrong password") == true)
    }

    @Test
    fun `login with exception shows errorMessage`() = runTest {
        // Given
        coEvery { mockRepository.login(any(), any()) } returns Result.failure(Exception("Network error"))

        viewModel = LoginViewModel(mockRepository)
        viewModel.onUsernameChange("20240001")
        viewModel.onPasswordChange("password")

        // When
        viewModel.login()
        advanceUntilIdle()

        // Then
        val state = viewModel.uiState.value
        assertNotNull(state.errorMessage)
        assertFalse(state.isLoggedIn)
    }

    @Test
    fun `login sets isLoading state correctly`() = runTest {
        // Given
        coEvery { mockRepository.login(any(), any()) } coAnswers {
            delay(100)
            Result.success(LoginResult(success = true))
        }
        coEvery { mockRepository.fetchRemoteSchedule(any()) } returns Result.success(emptyList())

        viewModel = LoginViewModel(mockRepository)
        viewModel.onUsernameChange("20240001")
        viewModel.onPasswordChange("password")

        // When
        viewModel.login()

        // Then - check loading state during login
        assertTrue(viewModel.uiState.value.isLoading)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isLoading)
    }

    // ================ 输入更新测试 ================

    @Test
    fun `onUsernameChange updates username and clears error`() = runTest {
        // Given
        viewModel = LoginViewModel(mockRepository)

        // When
        viewModel.onUsernameChange("20240001")

        // Then
        val state = viewModel.uiState.value
        assertEquals("20240001", state.username)
        assertNull(state.usernameError)
    }

    @Test
    fun `onPasswordChange updates password and clears error`() = runTest {
        // Given
        viewModel = LoginViewModel(mockRepository)

        // When
        viewModel.onPasswordChange("password123")

        // Then
        val state = viewModel.uiState.value
        assertEquals("password123", state.password)
        assertNull(state.passwordError)
    }

    // ================ 表单验证测试 ================

    @Test
    fun `isFormValid returns false when username is empty`() = runTest {
        // Given
        viewModel = LoginViewModel(mockRepository)

        viewModel.onPasswordChange("password")

        // When & Then
        assertFalse(viewModel.uiState.value.isFormValid)
    }

    @Test
    fun `isFormValid returns false when password is empty`() = runTest {
        // Given
        viewModel = LoginViewModel(mockRepository)

        viewModel.onUsernameChange("20240001")

        // When & Then
        assertFalse(viewModel.uiState.value.isFormValid)
    }

    @Test
    fun `isFormValid returns true when all fields are filled`() = runTest {
        // Given
        viewModel = LoginViewModel(mockRepository)

        viewModel.onUsernameChange("20240001")
        viewModel.onPasswordChange("password")

        // When & Then
        assertTrue(viewModel.uiState.value.isFormValid)
    }
}
