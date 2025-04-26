package com.example.finalproject.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.finalproject.R
import com.example.finalproject.auth.FirebaseAuthViewModel
import com.example.finalproject.auth.FirebaseAuthViewModelFactory
import com.example.finalproject.auth.LocalLoginState
import com.example.finalproject.ui.theme.FinalProjectTheme
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    authViewModel: FirebaseAuthViewModel = viewModel(factory = FirebaseAuthViewModelFactory(com.example.finalproject.MainApplication.instance)),
    onSignInSuccess: () -> Unit = {}
) {
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isSignUp by remember { mutableStateOf(false) }
    var showErrorDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    
    val loginState by authViewModel.loginState.collectAsState()
    
    val focusManager = LocalFocusManager.current
    
    LaunchedEffect(loginState) {
        when (loginState) {
            is LocalLoginState.Success -> {
                onSignInSuccess()
                authViewModel.resetLoginState()
            }
            is LocalLoginState.Error -> {
                errorMessage = (loginState as LocalLoginState.Error).message
                showErrorDialog = true
                authViewModel.resetLoginState()
            }
            else -> {}
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFCEEAC4))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.wheel_placeholder),
                    contentDescription = "App Logo",
                    modifier = Modifier.size(80.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            Text(
                text = if (isSignUp) "Create Account" else "Login",
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp
            )
            
            Spacer(modifier = Modifier.height(30.dp))
            
            if (isSignUp) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it.trim() },
                    label = { Text("Username") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Username Icon"
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp)),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        containerColor = Color.White,
                        focusedBorderColor = Color(0xFF4CAF50),
                        cursorColor = Color(0xFF4CAF50)
                    )
                )
                
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            OutlinedTextField(
                value = email,
                onValueChange = { email = it.trim() },
                label = { Text("Email") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = "Email Icon"
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp)),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    containerColor = Color.White,
                    focusedBorderColor = Color(0xFF4CAF50),
                    cursorColor = Color(0xFF4CAF50)
                )
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Password Icon"
                    )
                },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) 
                                Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (passwordVisible) "Hide Password" else "Show Password"
                        )
                    }
                },
                singleLine = true,
                visualTransformation = if (passwordVisible) 
                    VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { 
                        focusManager.clearFocus()
                        handleAuthAction(
                            username = username,
                            email = email,
                            password = password,
                            isSignUp = isSignUp,
                            authViewModel = authViewModel
                        )
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp)),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    containerColor = Color.White,
                    focusedBorderColor = Color(0xFF4CAF50),
                    cursorColor = Color(0xFF4CAF50)
                )
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = {
                    focusManager.clearFocus()
                    handleAuthAction(
                        username = username,
                        email = email,
                        password = password,
                        isSignUp = isSignUp,
                        authViewModel = authViewModel
                    )
                },
                modifier = Modifier
                    .height(56.dp)
                    .width(150.dp)
                    .clip(CircleShape),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFF8D7DA)
                ),
                enabled = loginState != LocalLoginState.Loading && 
                         password.isNotBlank() && 
                         email.isNotBlank() &&
                         (!isSignUp || username.isNotBlank())
            ) {
                if (loginState == LocalLoginState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White
                    )
                } else {
                    Text(
                        text = if (isSignUp) "Sign Up" else "Login",
                        color = Color.Black,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            TextButton(
                onClick = { isSignUp = !isSignUp },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (isSignUp) "Already have an account? Login" else "Don't have an account? Sign Up",
                    color = Color(0xFF1976D2),
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }
        }
        
        if (showErrorDialog) {
            AlertDialog(
                onDismissRequest = { showErrorDialog = false },
                title = { Text("Error") },
                text = { Text(errorMessage) },
                confirmButton = {
                    TextButton(onClick = { showErrorDialog = false }) {
                        Text("OK")
                    }
                }
            )
        }
        
        if (loginState == LocalLoginState.Loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(60.dp),
                    color = Color.White
                )
            }
        }
    }
}

private fun handleAuthAction(
    username: String,
    email: String,
    password: String,
    isSignUp: Boolean,
    authViewModel: FirebaseAuthViewModel
) {
    if ((isSignUp && (username.isBlank() || email.isBlank())) || (!isSignUp && email.isBlank()) || password.isBlank()) {
        return
    }
    
    if (isSignUp) {
        authViewModel.signUp(username, email, password)
    } else {
        authViewModel.signIn(email, password)
    }
}

@Preview(showBackground = true)
@Composable
fun LoginScreenPreview() {
    FinalProjectTheme {
        LoginScreen()
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    NavHost(navController, startDestination = "login") {
        composable("login") { 
            LoginScreen(onSignInSuccess = { 
                navController.navigate("home") 
            }) 
        }
        composable("home") { 
            HomeScreen(
                onCreateRoom = { },
                onJoinRoom = { },
                onNavigateToCalendar = { navController.navigate("calendar") }
            ) 
        }
        composable("calendar") {
            CalendarScreen(onBackToHome = { navController.popBackStack() })
        }
    }
} 