package com.azu.timetable.data.repository

import com.azu.timetable.data.model.SlotType
import com.azu.timetable.data.model.TimetableSlot

object DefaultTimetable {
    fun getInitialSlots(): List<TimetableSlot> {
        val slots = mutableListOf<TimetableSlot>()

        // Monday (1)
        slots.add(TimetableSlot(dayOfWeek = 1, periodNumber = 1, title = "Environmental Science & Sustainability", courseCode = "GE23311", faculty = "Dr. S. Harikumar", venue = "A204", startTime = "08:00", endTime = "08:50", slotType = SlotType.CLASS))
        slots.add(TimetableSlot(dayOfWeek = 1, periodNumber = 2, title = "Discrete Mathematics", courseCode = "MA23311", faculty = "Dr. V. Sathishkumar", venue = "A204", startTime = "08:50", endTime = "09:40", slotType = SlotType.CLASS))
        slots.add(TimetableSlot(dayOfWeek = 1, periodNumber = 0, title = "Morning Break", venue = "Campus", startTime = "09:40", endTime = "10:00", slotType = SlotType.BREAK))
        slots.add(TimetableSlot(dayOfWeek = 1, periodNumber = 3, title = "Digital Principles & Computer Org", courseCode = "EC23331", faculty = "Ms. V. Subashini", venue = "A204", startTime = "10:00", endTime = "10:50", slotType = SlotType.CLASS))
        slots.add(TimetableSlot(dayOfWeek = 1, periodNumber = 4, title = "Artificial Intelligence", courseCode = "AL23311", faculty = "Dr. SelvaKumari", venue = "A204", startTime = "10:50", endTime = "11:40", slotType = SlotType.CLASS))
        slots.add(TimetableSlot(dayOfWeek = 1, periodNumber = 0, title = "Lunch Break", venue = "Cafeteria", startTime = "11:40", endTime = "12:00", slotType = SlotType.BREAK))
        slots.add(TimetableSlot(dayOfWeek = 1, periodNumber = 5, title = "DPCO Laboratory (L21, L22)", courseCode = "EC23331", faculty = "Ms. V. Subashini & Mr. M. Ashok", venue = "DPCO Lab", startTime = "12:00", endTime = "13:40", slotType = SlotType.LAB))
        slots.add(TimetableSlot(dayOfWeek = 1, periodNumber = 7, title = "Club Activity", courseCode = "CLUB", faculty = "Club In-Charge", venue = "A204", startTime = "14:00", endTime = "15:40", slotType = SlotType.ACTIVITY))

        // Tuesday (2)
        slots.add(TimetableSlot(dayOfWeek = 2, periodNumber = 1, title = "Data Structures", courseCode = "CS23311", faculty = "Ms. R. Gaja Lakshmi", venue = "A204", startTime = "08:00", endTime = "08:50", slotType = SlotType.CLASS))
        slots.add(TimetableSlot(dayOfWeek = 2, periodNumber = 2, title = "Object Oriented Programming", courseCode = "CS23312", faculty = "Mr. J. Praveen Kumar", venue = "A204", startTime = "08:50", endTime = "09:40", slotType = SlotType.CLASS))
        slots.add(TimetableSlot(dayOfWeek = 2, periodNumber = 0, title = "Morning Break", venue = "Campus", startTime = "09:40", endTime = "10:00", slotType = SlotType.BREAK))
        slots.add(TimetableSlot(dayOfWeek = 2, periodNumber = 3, title = "Environmental Science & Sustainability", courseCode = "GE23311", faculty = "Dr. S. Harikumar", venue = "A204", startTime = "10:00", endTime = "10:50", slotType = SlotType.CLASS))
        slots.add(TimetableSlot(dayOfWeek = 2, periodNumber = 4, title = "Discrete Mathematics", courseCode = "MA23311", faculty = "Dr. V. Sathishkumar", venue = "A204", startTime = "10:50", endTime = "11:40", slotType = SlotType.CLASS))
        slots.add(TimetableSlot(dayOfWeek = 2, periodNumber = 0, title = "Lunch Break", venue = "Cafeteria", startTime = "11:40", endTime = "12:00", slotType = SlotType.BREAK))
        slots.add(TimetableSlot(dayOfWeek = 2, periodNumber = 5, title = "Mini Project / Centre Activity", courseCode = "PROJ", faculty = "Faculty In-Charge", venue = "A03 Centre", startTime = "12:00", endTime = "13:40", slotType = SlotType.ACTIVITY))
        slots.add(TimetableSlot(dayOfWeek = 2, periodNumber = 7, title = "Club Activity", courseCode = "CLUB", faculty = "Club In-Charge", venue = "A204", startTime = "14:00", endTime = "15:40", slotType = SlotType.ACTIVITY))

        // Wednesday (3)
        slots.add(TimetableSlot(dayOfWeek = 3, periodNumber = 1, title = "Digital Principles & Computer Org", courseCode = "EC23331", faculty = "Ms. V. Subashini", venue = "A204", startTime = "08:00", endTime = "08:50", slotType = SlotType.CLASS))
        slots.add(TimetableSlot(dayOfWeek = 3, periodNumber = 2, title = "Artificial Intelligence", courseCode = "AL23311", faculty = "Dr. SelvaKumari", venue = "A204", startTime = "08:50", endTime = "09:40", slotType = SlotType.CLASS))
        slots.add(TimetableSlot(dayOfWeek = 3, periodNumber = 0, title = "Morning Break", venue = "Campus", startTime = "09:40", endTime = "10:00", slotType = SlotType.BREAK))
        slots.add(TimetableSlot(dayOfWeek = 3, periodNumber = 3, title = "Data Structures", courseCode = "CS23311", faculty = "Ms. R. Gaja Lakshmi", venue = "A204", startTime = "10:00", endTime = "10:50", slotType = SlotType.CLASS))
        slots.add(TimetableSlot(dayOfWeek = 3, periodNumber = 4, title = "Object Oriented Programming", courseCode = "CS23312", faculty = "Mr. J. Praveen Kumar", venue = "A204", startTime = "10:50", endTime = "11:40", slotType = SlotType.CLASS))
        slots.add(TimetableSlot(dayOfWeek = 3, periodNumber = 0, title = "Lunch Break", venue = "Cafeteria", startTime = "11:40", endTime = "12:00", slotType = SlotType.BREAK))
        slots.add(TimetableSlot(dayOfWeek = 3, periodNumber = 5, title = "Mentoring Session", courseCode = "MENTOR", faculty = "Ms. R. Gaja Lakshmi", venue = "Steve Jobs 180 Lab", startTime = "12:00", endTime = "12:50", slotType = SlotType.MENTORING))
        slots.add(TimetableSlot(dayOfWeek = 3, periodNumber = 6, title = "Centre Activity", courseCode = "ACT", faculty = "Centre In-Charge", venue = "A204", startTime = "12:50", endTime = "13:40", slotType = SlotType.ACTIVITY))
        slots.add(TimetableSlot(dayOfWeek = 3, periodNumber = 7, title = "OOP Laboratory (L31, L32)", courseCode = "CS23322", faculty = "Mr. J. Praveen Kumar & Ms. V. Subashini", venue = "A201 OOP Lab", startTime = "14:00", endTime = "15:40", slotType = SlotType.LAB))

        // Thursday (4)
        slots.add(TimetableSlot(dayOfWeek = 4, periodNumber = 1, title = "Discrete Mathematics", courseCode = "MA23311", faculty = "Dr. V. Sathishkumar", venue = "A204", startTime = "08:00", endTime = "08:50", slotType = SlotType.CLASS))
        slots.add(TimetableSlot(dayOfWeek = 4, periodNumber = 2, title = "Digital Principles & Computer Org", courseCode = "EC23331", faculty = "Ms. V. Subashini", venue = "A204", startTime = "08:50", endTime = "09:40", slotType = SlotType.CLASS))
        slots.add(TimetableSlot(dayOfWeek = 4, periodNumber = 0, title = "Morning Break", venue = "Campus", startTime = "09:40", endTime = "10:00", slotType = SlotType.BREAK))
        slots.add(TimetableSlot(dayOfWeek = 4, periodNumber = 3, title = "Artificial Intelligence", courseCode = "AL23311", faculty = "Dr. SelvaKumari", venue = "A204", startTime = "10:00", endTime = "10:50", slotType = SlotType.CLASS))
        slots.add(TimetableSlot(dayOfWeek = 4, periodNumber = 4, title = "Data Structures", courseCode = "CS23311", faculty = "Ms. R. Gaja Lakshmi", venue = "A204", startTime = "10:50", endTime = "11:40", slotType = SlotType.CLASS))
        slots.add(TimetableSlot(dayOfWeek = 4, periodNumber = 0, title = "Lunch Break", venue = "Cafeteria", startTime = "11:40", endTime = "12:00", slotType = SlotType.BREAK))
        slots.add(TimetableSlot(dayOfWeek = 4, periodNumber = 5, title = "DS Laboratory (L33, L34)", courseCode = "CS23321", faculty = "Ms. R. Gaja Lakshmi & Mr. M. Ashok", venue = "A202 DS Lab", startTime = "12:00", endTime = "13:40", slotType = SlotType.LAB))
        slots.add(TimetableSlot(dayOfWeek = 4, periodNumber = 7, title = "Club Activity", courseCode = "CLUB", faculty = "Club In-Charge", venue = "A204", startTime = "14:00", endTime = "15:40", slotType = SlotType.ACTIVITY))

        // Friday (5)
        slots.add(TimetableSlot(dayOfWeek = 5, periodNumber = 1, title = "Object Oriented Programming", courseCode = "CS23312", faculty = "Mr. J. Praveen Kumar", venue = "A204", startTime = "08:00", endTime = "08:50", slotType = SlotType.CLASS))
        slots.add(TimetableSlot(dayOfWeek = 5, periodNumber = 2, title = "Environmental Science & Sustainability", courseCode = "GE23311", faculty = "Dr. S. Harikumar", venue = "A204", startTime = "08:50", endTime = "09:40", slotType = SlotType.CLASS))
        slots.add(TimetableSlot(dayOfWeek = 5, periodNumber = 0, title = "Morning Break", venue = "Campus", startTime = "09:40", endTime = "10:00", slotType = SlotType.BREAK))
        slots.add(TimetableSlot(dayOfWeek = 5, periodNumber = 3, title = "Discrete Mathematics", courseCode = "MA23311", faculty = "Dr. V. Sathishkumar", venue = "A204", startTime = "10:00", endTime = "10:50", slotType = SlotType.CLASS))
        slots.add(TimetableSlot(dayOfWeek = 5, periodNumber = 4, title = "Digital Principles & Computer Org", courseCode = "EC23331", faculty = "Ms. V. Subashini", venue = "A204", startTime = "10:50", endTime = "11:40", slotType = SlotType.CLASS))
        slots.add(TimetableSlot(dayOfWeek = 5, periodNumber = 0, title = "Lunch Break", venue = "Cafeteria", startTime = "11:40", endTime = "12:00", slotType = SlotType.BREAK))
        slots.add(TimetableSlot(dayOfWeek = 5, periodNumber = 5, title = "Mentoring Session", courseCode = "MENTOR", faculty = "Mr. J. Praveen Kumar", venue = "Steve Jobs 180 Lab", startTime = "12:00", endTime = "12:50", slotType = SlotType.MENTORING))
        slots.add(TimetableSlot(dayOfWeek = 5, periodNumber = 6, title = "Centre Activity", courseCode = "ACT", faculty = "Centre In-Charge", venue = "A204", startTime = "12:50", endTime = "13:40", slotType = SlotType.ACTIVITY))
        slots.add(TimetableSlot(dayOfWeek = 5, periodNumber = 7, title = "Club Activity", courseCode = "CLUB", faculty = "Club In-Charge", venue = "A204", startTime = "14:00", endTime = "15:40", slotType = SlotType.ACTIVITY))

        // Saturday (6)
        slots.add(TimetableSlot(dayOfWeek = 6, periodNumber = 1, title = "Self Study & Assignment Review", courseCode = "STUDY", faculty = "Self Paced", venue = "Library / Study Hall", startTime = "09:00", endTime = "11:00", slotType = SlotType.CLASS))
        slots.add(TimetableSlot(dayOfWeek = 6, periodNumber = 0, title = "Tea Break", venue = "Cafeteria", startTime = "11:00", endTime = "11:30", slotType = SlotType.BREAK))
        slots.add(TimetableSlot(dayOfWeek = 6, periodNumber = 2, title = "Design Thinking & Software Eng Lab", courseCode = "CS23IC1", faculty = "Mr. J. Praveen Kumar", venue = "Steve Jobs Lab", startTime = "11:30", endTime = "13:00", slotType = SlotType.LAB))

        return slots
    }
}
