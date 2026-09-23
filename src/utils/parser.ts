export type WeekType = 'WEEKLY' | 'EVEN' | 'ODD';

export interface ClassSession {
  day: string;
  dayOfWeek: number; // 0: شنبه, 1: یکشنبه, 2: دوشنبه, 3: سه‌شنبه, 4: چهارشنبه, 5: پنج‌شنبه, 6: جمعه
  startTime: string; // HH:mm
  endTime: string; // HH:mm
  durationMinutes: number;
  period: 'هر هفته' | 'هفته در میان';
  weekParity: 'زوج' | 'فرد' | 'هردو';
  weekType: WeekType; // Exact match with Android Room Entity (class_sessions.weekType)
  location: string;
  isVirtualOrProject?: boolean;
}

export interface ExamInfo {
  dayDesc?: string;
  time?: string;
  location?: string;
  date?: string;
}

export interface CourseOffering {
  groupCode: string;
  instructor: string;
  capacity: number;
  enrolled: number;
  reserved: number;
  degree?: string;
  department?: string;
  sessions: ClassSession[];
  exam?: ExamInfo | null;
  notes?: string;
  rawDetails?: string;
}

export interface ParsedCourse {
  courseCode: string;
  name: string;
  credits: number;
  faculty: string;
  department: string;
  offerings: CourseOffering[];
}

export interface RoomCourseExport {
  code: string;
  name: string;
  department: string;
  credits: number;
  isSelectedForGeneration: boolean;
  sections: {
    sectionCode: string;
    instructor: string;
    capacity: number;
    examDate: string;
    examStartTime: string;
    examEndTime: string;
    isEnrolled: boolean;
    sessions: {
      dayOfWeek: number;
      startTime: string;
      endTime: string;
      location: string;
      weekType: WeekType;
    }[];
  }[];
}

/**
 * Clean & strip HTML tags and decode entities from tooltip text
 */
export function cleanTooltipText(raw: string): string {
  if (!raw) return '';
  let text = raw
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&amp;/g, '&')
    .replace(/&nbsp;/g, ' ')
    .replace(/<br\s*[\/]?>/gi, ' ')
    .replace(/<[^>]*>/g, ' ')
    .replace(/[\u200B-\u200D\uFEFF]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
  return text;
}

/**
 * Extract classroom location from inside parentheses
 * e.g. "هر هفته به مدت 120 دقیقه در کلاس 10" -> "کلاس 10"
 * e.g. "هفته در میان به مدت 120 دقیقه در کارگاه کامپیوتر 2" -> "کارگاه کامپیوتر 2"
 */
export function extractLocation(details: string): string {
  // Pattern 1: right after "دقیقه در"
  const m1 = details.match(/دقیقه\s*در\s+(.+)$/);
  if (m1) return m1[1].trim();

  // Pattern 2: after the last "در"
  const m2 = details.match(/در\s+([^در]+)$/);
  if (m2) return m2[1].trim();

  return '';
}

export function parseUniversityHtml(htmlContent: string): ParsedCourse[] {
  const parser = new DOMParser();
  const doc = parser.parseFromString(htmlContent, 'text/html');

  // Look for target table
  const tables = doc.querySelectorAll('table');
  let targetTable: HTMLTableElement | null = null;

  for (let i = 0; i < tables.length; i++) {
    const table = tables[i];
    const headerText = table.textContent || '';
    if (headerText.includes('شماره درس') && headerText.includes('نام درس')) {
      targetTable = table;
      break;
    }
  }

  if (!targetTable) {
    targetTable = doc.querySelector("table[border='1']");
  }

  if (!targetTable) {
    return [];
  }

  // Pre-index any floating detail divs like <div class="coursedetailhead"> / <div class="coursedetail">
  const floatingDetailMap = new Map<string, string>();
  const heads = doc.querySelectorAll('.coursedetailhead');
  heads.forEach((h) => {
    const headText = h.textContent?.trim() || '';
    const body = h.nextElementSibling;
    if (body && body.classList.contains('coursedetail')) {
      floatingDetailMap.set(headText, body.innerHTML || body.textContent || '');
    }
  });

  const rows = targetTable.querySelectorAll('tr');
  const coursesMap = new Map<string, ParsedCourse>();

  for (let i = 0; i < rows.length; i++) {
    const tr = rows[i];
    if (tr.querySelector('th')) continue; // Skip header row

    const cells = tr.querySelectorAll('td');
    if (cells.length < 9) continue;

    const courseCode = cells[1].textContent?.trim() || '';
    const groupCode = cells[2].textContent?.trim() || '1';
    const courseName = cells[3].textContent?.trim() || '';
    const unitsStr = cells[4].textContent?.trim() || '0';
    const credits = Math.round(parseFloat(unitsStr) || 0);
    const enrolled = parseInt(cells[5].textContent?.trim() || '0', 10) || 0;
    const capacity = parseInt(cells[6].textContent?.trim() || '0', 10) || 0;
    const faculty = cells[7].textContent?.trim() || '';
    const professor = (cells[8].textContent || '').replace(/\s+/g, ' ').trim();
    const reserved = cells.length > 12 ? parseInt(cells[12].textContent?.trim() || '0', 10) || 0 : 0;

    if (!courseCode || !courseName) continue;

    // Search for tooltip from img[title], a[title], or any tag having title with details
    let rawTooltip = '';
    const titleElements = tr.querySelectorAll('[title]');
    for (let t = 0; t < titleElements.length; t++) {
      const val = titleElements[t].getAttribute('title') || '';
      if (val.includes('جلسه') || val.includes('body=') || val.includes('header=') || val.includes('امتحان')) {
        rawTooltip = val;
        break;
      }
    }

    // Fallback: check if we have a floating detail div that matches this course + group
    if (!rawTooltip) {
      const matchKey = `${courseName} گروه ${groupCode}`;
      if (floatingDetailMap.has(matchKey)) {
        rawTooltip = floatingDetailMap.get(matchKey)!;
      }
    }

    const cleanedTooltip = cleanTooltipText(rawTooltip);

    const sessions = parseSessionsFromCleanedText(cleanedTooltip);
    const exam = parseExamFromCleanedText(cleanedTooltip);

    const degreeMatch = cleanedTooltip.match(/مقطع:\s*([^\s-]+(?: [^\s-]+)*)/);
    const degree = degreeMatch ? degreeMatch[1].trim() : '';

    const deptMatch = cleanedTooltip.match(/گروه آموزشی:\s*([^\s-]+(?: [^\s-]+)*)/);
    const department = deptMatch ? deptMatch[1].trim() : 'كامپيوتر';

    const notesMatch = cleanedTooltip.match(/(?:تذکر|تدکر):?\s*([^\]]+)/);
    const notes = notesMatch ? notesMatch[1].trim() : undefined;

    const offering: CourseOffering = {
      groupCode,
      instructor: professor || 'گروه آموزشی',
      capacity,
      enrolled,
      reserved,
      degree,
      department,
      sessions,
      exam,
      notes,
      rawDetails: cleanedTooltip
    };

    if (!coursesMap.has(courseCode)) {
      coursesMap.set(courseCode, {
        courseCode,
        name: courseName,
        credits: credits > 0 ? credits : 1,
        faculty: faculty || 'دانشکده اصلی',
        department: department || 'كامپيوتر',
        offerings: [offering]
      });
    } else {
      const existing = coursesMap.get(courseCode)!;
      const exists = existing.offerings.some(o => o.groupCode === groupCode);
      if (!exists) {
        existing.offerings.push(offering);
      }
    }
  }

  return Array.from(coursesMap.values());
}

/**
 * Extracts class sessions from cleaned text
 * Example input:
 * "جلسه اول روز: دوشنبه ساعت 14(هر هفته به مدت 120 دقیقه در کارگاه کامپیوتر 1) شروع زوج جلسه دوم روز: دوشنبه ساعت 16(هر هفته به مدت 120 دقیقه در کارگاه کامپیوتر 1) شروع فرد"
 * or "جلسه اول روز: سه شنبه ساعت 15(هر هفته به مدت 120 دقیقه در کلاس 17) شروع فرد"
 */
export function parseSessionsFromCleanedText(cleanedText: string): ClassSession[] {
  const sessions: ClassSession[] = [];
  if (!cleanedText) return sessions;

  // Pattern captures:
  // 1: day name (non-greedy between روز: and ساعت)
  // 2: start hour
  // 3: optional start minute
  // 4: details inside parentheses
  // 5: optional parity (زوج or فرد)
  const sessionRegex = /جلسه\s*(?:اول|دوم|سوم|چهارم|پنجم)?\s*روز:\s*(.*?)\s*ساعت\s*(\d+)(?:\:(\d+))?\s*\(([^)]+)\)\s*(?:شروع\s*(زوج|فرد))?/g;
  let match;

  while ((match = sessionRegex.exec(cleanedText)) !== null) {
    const rawDay = match[1] ? match[1].trim() : '';
    const normalizedDay = normalizeDayName(rawDay);

    // If day is missing or invalid, skip (e.g. project rows with duration 0 and empty day)
    if (!normalizedDay) continue;

    const dayOfWeek = dayNameToNumber(normalizedDay);
    if (dayOfWeek === -1) continue;

    const hour = parseInt(match[2], 10);
    const minute = match[3] ? parseInt(match[3], 10) : 0;
    const details = match[4] || '';
    const parity = (match[5] as 'زوج' | 'فرد') || 'هردو';

    const durMatch = details.match(/به مدت\s*(\d+)\s*دقیقه/);
    const duration = durMatch ? parseInt(durMatch[1], 10) : 90;

    // Ignore placeholder sessions with duration 0 (e.g. project, internship)
    if (duration === 0) continue;

    let location = extractLocation(details);
    if (location === '0') location = '';

    const period = details.includes('هفته در میان') ? 'هفته در میان' : 'هر هفته';

    let weekType: WeekType = 'WEEKLY';
    if (period === 'هفته در میان') {
      weekType = parity === 'زوج' ? 'EVEN' : parity === 'فرد' ? 'ODD' : 'WEEKLY';
    }

    const startTotalMinutes = hour * 60 + minute;
    const endTotalMinutes = startTotalMinutes + (duration > 0 ? duration : 90);
    const startStr = `${String(Math.floor(startTotalMinutes / 60)).padStart(2, '0')}:${String(startTotalMinutes % 60).padStart(2, '0')}`;
    const endStr = `${String(Math.floor(endTotalMinutes / 60)).padStart(2, '0')}:${String(endTotalMinutes % 60).padStart(2, '0')}`;

    sessions.push({
      day: normalizedDay,
      dayOfWeek,
      startTime: startStr,
      endTime: endStr,
      durationMinutes: duration,
      period,
      weekParity: parity,
      weekType,
      location: location || 'کلاس نامشخص'
    });
  }

  return sessions;
}

export function parseExamFromCleanedText(cleanedText: string): ExamInfo | null {
  if (!cleanedText) return null;
  // e.g. "امتحان روز: هفتم ساعت 14 به مدت 0 دقیقه در کلاس 0 به تاریخ 1405/03/20"
  const examMatch = cleanedText.match(/امتحان روز:\s*([^\s]*)\s*ساعت\s*(\d+)(?:\:(\d+))?[^در]*در کلاس\s*([^\s]*)\s*(?:به تاریخ\s*([^\s<]+))?/);
  if (examMatch) {
    const rawHour = examMatch[2] ? parseInt(examMatch[2], 10) : 0;
    const rawMin = examMatch[3] ? parseInt(examMatch[3], 10) : 0;
    
    const loc = examMatch[4] === '0' ? '' : examMatch[4] || '';
    const date = examMatch[5] || '';
    const dayDesc = examMatch[1]?.trim() || '';

    // If day, date, and location are dummy (e.g. 6 am, class 0), ignore
    if (!date && loc === '' && (rawHour === 6 || rawHour === 0) && !dayDesc) {
      return null;
    }

    return {
      dayDesc,
      time: `${String(rawHour).padStart(2, '0')}:${String(rawMin).padStart(2, '0')}`,
      location: loc,
      date
    };
  }
  return null;
}

export function normalizeDayName(day: string): string {
  const clean = day.replace(/[\u200B-\u200D\uFEFF]/g, '').replace(/\s+/g, ' ').trim();
  if (clean.includes('جمعه')) return 'جمعه';
  if (clean.includes('پنج')) return 'پنج‌شنبه';
  if (clean.includes('چهار')) return 'چهارشنبه';
  if (clean.includes('سه')) return 'سه‌شنبه';
  if (clean.includes('دو')) return 'دوشنبه';
  if (clean.includes('یک')) return 'یکشنبه';
  if (clean.includes('شنبه')) return 'شنبه';
  return '';
}

export function dayNameToNumber(day: string): number {
  switch (day) {
    case 'شنبه': return 0;
    case 'یکشنبه': return 1;
    case 'دوشنبه': return 2;
    case 'سه‌شنبه': return 3;
    case 'چهارشنبه': return 4;
    case 'پنج‌شنبه': return 5;
    case 'جمعه': return 6;
    default: return -1;
  }
}

/**
 * Converts parsed courses directly into Room Database export structure
 * for ir.courseplanner.app
 */
export function convertToRoomDatabaseFormat(courses: ParsedCourse[]): { version: number; courses: RoomCourseExport[] } {
  return {
    version: 3,
    courses: courses.map(c => ({
      code: c.courseCode,
      name: c.name,
      department: c.department || 'كامپيوتر',
      credits: c.credits,
      isSelectedForGeneration: false,
      sections: c.offerings.map(o => ({
        sectionCode: o.groupCode,
        instructor: o.instructor,
        capacity: o.capacity,
        examDate: o.exam?.date || '',
        examStartTime: o.exam?.time || '',
        examEndTime: o.exam?.time ? addHours(o.exam.time, 2) : '',
        isEnrolled: false,
        sessions: o.sessions.map(s => ({
          dayOfWeek: s.dayOfWeek,
          startTime: s.startTime,
          endTime: s.endTime,
          location: s.location,
          weekType: s.weekType
        }))
      }))
    }))
  };
}

function addHours(timeStr: string, hours: number): string {
  const [h, m] = timeStr.split(':').map(Number);
  const newH = (h + hours) % 24;
  return `${String(newH).padStart(2, '0')}:${String(m || 0).padStart(2, '0')}`;
}
