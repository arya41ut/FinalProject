package com.example.finalproject.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.finalproject.model.CalendarEvent
import com.example.finalproject.model.CalendarRepository
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    onBackToHome: () -> Unit,
    selectedRestaurant: String? = null
) {
    var currentMonth by remember { mutableStateOf(YearMonth.now()) }
    
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    
    var showRestaurantDialog by remember { mutableStateOf(false) }
    var showAddRestaurantDialog by remember { mutableStateOf(false) }
    var restaurantNameInput by remember { mutableStateOf("") }
    var notesInput by remember { mutableStateOf("") }
    
    LaunchedEffect(selectedRestaurant) {
        if (selectedRestaurant != null && selectedRestaurant != "null") {
            if (selectedDate == null) {
                selectedDate = LocalDate.now()
            }
            
            restaurantNameInput = selectedRestaurant
            showAddRestaurantDialog = true
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calendar View") },
                navigationIcon = {
                    IconButton(onClick = onBackToHome) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back to Home")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    currentMonth = currentMonth.minusMonths(1)
                }) {
                    Text("<", fontSize = 20.sp)
                }
                
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Text(
                        text = currentMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                
                IconButton(onClick = {
                    currentMonth = currentMonth.plusMonths(1)
                }) {
                    Text(">", fontSize = 20.sp)
                }
            }
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(8.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (day in listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = day,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
                
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                
                val datesWithEvents = CalendarRepository.getDatesWithEvents()
                
                val firstDayOfMonth = currentMonth.atDay(1)
                val lastDayOfMonth = currentMonth.atEndOfMonth()
                val firstDayOfCalendar = firstDayOfMonth.minusDays(firstDayOfMonth.dayOfWeek.value % 7L)
                
                val weeks = mutableListOf<List<LocalDate?>>()
                var currentDate = firstDayOfCalendar
                
                while (currentDate.isBefore(lastDayOfMonth) || currentDate.month == lastDayOfMonth.month) {
                    val week = (0..6).map { dayOfWeek ->
                        val date = currentDate.plusDays(dayOfWeek.toLong())
                        if (date.month == currentMonth.month) date else null
                    }
                    weeks.add(week)
                    currentDate = currentDate.plusWeeks(1)
                }
                
                weeks.forEach { week ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        week.forEach { date ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .padding(4.dp)
                                    .clip(CircleShape)
                                    .clickable(enabled = date != null) {
                                        date?.let {
                                            selectedDate = it
                                            val event = CalendarRepository.getEvent(it)
                                            if (event != null) {
                                                restaurantNameInput = event.restaurantName
                                                notesInput = event.notes
                                                showRestaurantDialog = true
                                            } else if (selectedRestaurant != null) {
                                                restaurantNameInput = selectedRestaurant
                                                notesInput = ""
                                                showAddRestaurantDialog = true
                                            } else {
                                                showAddRestaurantDialog = true
                                            }
                                        }
                                    }
                                    .background(
                                        when {
                                            date == selectedDate -> MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                            date != null && date == LocalDate.now() -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                                            else -> Color.Transparent
                                        }
                                    )
                                    .border(
                                        width = if (date == selectedDate) 2.dp else 0.dp,
                                        color = if (date == selectedDate) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (date != null) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = date.dayOfMonth.toString(),
                                            fontWeight = if (date == LocalDate.now()) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 16.sp
                                        )
                                        
                                        if (date in datesWithEvents) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Icon(
                                                imageVector = Icons.Default.Restaurant,
                                                contentDescription = "Restaurant scheduled",
                                                modifier = Modifier.size(12.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            selectedDate?.let { date ->
                val event = CalendarRepository.getEvent(date)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Selected: ${date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy"))}",
                            fontWeight = FontWeight.Medium,
                            fontSize = 16.sp
                        )
                        
                        if (event != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Restaurant: ${event.restaurantName}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            
                            if (event.notes.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Notes: ${event.notes}",
                                    fontStyle = FontStyle.Italic,
                                    fontSize = 14.sp
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    selectedDate?.let { CalendarRepository.removeEvent(it) }
                                }
                            ) {
                                Text("Remove Restaurant")
                            }
                        } else {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No restaurant selected for this date",
                                fontStyle = FontStyle.Italic
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    showAddRestaurantDialog = true
                                }
                            ) {
                                Text("Add Restaurant")
                            }
                        }
                    }
                }
            } ?: run {
                Text(
                    text = "Select a date to add or view a restaurant",
                    modifier = Modifier.padding(16.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    
    if (showRestaurantDialog && selectedDate != null) {
        val event = selectedDate?.let { CalendarRepository.getEvent(it) }
        
        if (event != null) {
            Dialog(onDismissRequest = { showRestaurantDialog = false }) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Restaurant Details",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            
                            IconButton(onClick = { showRestaurantDialog = false }) {
                                Icon(Icons.Default.Close, contentDescription = "Close")
                            }
                        }
                        
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                        
                        Text(
                            text = selectedDate!!.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")),
                            fontWeight = FontWeight.Medium,
                            fontSize = 18.sp
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = event.restaurantName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        if (event.notes.isNotBlank()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Text(
                                text = "Notes:",
                                fontWeight = FontWeight.Medium,
                                fontSize = 16.sp
                            )
                            
                            Text(
                                text = event.notes,
                                fontSize = 16.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Button(
                                onClick = {
                                    selectedDate?.let { CalendarRepository.removeEvent(it) }
                                    showRestaurantDialog = false
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text("Remove")
                            }
                            
                            Button(
                                onClick = {
                                    showRestaurantDialog = false
                                    restaurantNameInput = event.restaurantName
                                    notesInput = event.notes
                                    showAddRestaurantDialog = true
                                }
                            ) {
                                Text("Edit")
                            }
                        }
                    }
                }
            }
        }
    }
    
    if (showAddRestaurantDialog && selectedDate != null) {
        Dialog(onDismissRequest = { showAddRestaurantDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Add Restaurant",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        
                        IconButton(onClick = { showAddRestaurantDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                    
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    
                    Text(
                        text = selectedDate!!.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")),
                        fontWeight = FontWeight.Medium,
                        fontSize = 18.sp
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OutlinedTextField(
                        value = restaurantNameInput,
                        onValueChange = { restaurantNameInput = it },
                        label = { Text("Restaurant Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OutlinedTextField(
                        value = notesInput,
                        onValueChange = { notesInput = it },
                        label = { Text("Notes (Optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = { showAddRestaurantDialog = false }
                        ) {
                            Text("Cancel")
                        }
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        Button(
                            onClick = {
                                if (restaurantNameInput.isNotBlank()) {
                                    selectedDate?.let {
                                        CalendarRepository.addEvent(it, restaurantNameInput, notesInput)
                                    }
                                    showAddRestaurantDialog = false
                                }
                            },
                            enabled = restaurantNameInput.isNotBlank()
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
} 