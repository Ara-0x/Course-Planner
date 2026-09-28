package ir.courseplanner.app.data.model

import java.util.Locale

/**
 * Canonical form of a course/section code.
 *
 * Every uniqueness check (`CourseRepository`) and every UI pre-check must use
 * this single definition, so that " math101 ", "MATH101" and "Math101" are the
 * same code. Without it, the app happily stored two rows for one course simply
 * because of a stray space or a lower-case letter.
 *
 * NOTE: the *stored* value keeps the user's casing (only trimmed); only the
 * comparison is case/space insensitive. Portal codes are numeric, so this never
 * changes how a re-import matches its own courses.
 */
fun normalizeCode(raw: String): String = raw.trim().uppercase(Locale.ROOT)
