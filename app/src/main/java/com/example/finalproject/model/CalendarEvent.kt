package com.example.finalproject.model

import java.time.LocalDate

data class CalendarEvent(
    val date: LocalDate,
    val restaurantName: String,
    val notes: String = ""
)

class CalendarRepository {
    companion object {
        private val events = mutableMapOf<LocalDate, CalendarEvent>()
        
        fun addEvent(date: LocalDate, restaurantName: String, notes: String = "") {
            events[date] = CalendarEvent(date, restaurantName, notes)
        }
        
        fun getEvent(date: LocalDate): CalendarEvent? {
            return events[date]
        }
        
        fun getAllEvents(): Map<LocalDate, CalendarEvent> {
            return events.toMap()
        }
        
        fun getDatesWithEvents(): Set<LocalDate> {
            return events.keys.toSet()
        }
        
        fun removeEvent(date: LocalDate) {
            events.remove(date)
        }
        
        fun clearAllEvents() {
            events.clear()
        }
    }
} 