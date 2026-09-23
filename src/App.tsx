import React, { useState, useMemo, useEffect } from 'react';
import { 
  Plus, 
  Search, 
  Check, 
  Calendar, 
  Clock, 
  Trash2, 
  Download, 
  Copy, 
  Database, 
  AlertTriangle, 
  Settings as SettingsIcon, 
  BookOpen, 
  Layers, 
  X, 
  FileText, 
  Sparkles, 
  CheckCircle2, 
  ChevronLeft, 
  LayoutGrid, 
  List, 
  Upload, 
  FileUp, 
  RotateCcw,
  User,
  GraduationCap,
  Sliders,
  ExternalLink,
  ShieldAlert,
  Info,
  GitBranch,
  RefreshCw,
  FolderDown
} from 'lucide-react';
import { 
  parseUniversityHtml, 
  ParsedCourse, 
  CourseOffering, 
  convertToRoomDatabaseFormat 
} from './utils/parser';

const DAYS_OF_WEEK = [
  { name: 'شنبه', index: 0 },
  { name: 'یکشنبه', index: 1 },
  { name: 'دوشنبه', index: 2 },
  { name: 'سه‌شنبه', index: 3 },
  { name: 'چهارشنبه', index: 4 },
  { name: 'پنج‌شنبه', index: 5 }
];

const TIME_SLOTS = [
  '08:00', '09:00', '10:00', '11:00', '12:00', '13:00', 
  '14:00', '15:00', '16:00', '17:00', '18:00', '19:00'
];

const COURSE_COLOR_SCHEMES = [
  { bg: 'bg-indigo-950/80', border: 'border-indigo-500/40', text: 'text-indigo-200', tag: 'bg-indigo-500/20 text-indigo-300' },
  { bg: 'bg-sky-950/80', border: 'border-sky-500/40', text: 'text-sky-200', tag: 'bg-sky-500/20 text-sky-300' },
  { bg: 'bg-emerald-950/80', border: 'border-emerald-500/40', text: 'text-emerald-200', tag: 'bg-emerald-500/20 text-emerald-300' },
  { bg: 'bg-amber-950/80', border: 'border-amber-500/40', text: 'text-amber-200', tag: 'bg-amber-500/20 text-amber-300' },
  { bg: 'bg-rose-950/80', border: 'border-rose-500/40', text: 'text-rose-200', tag: 'bg-rose-500/20 text-rose-300' },
  { bg: 'bg-purple-950/80', border: 'border-purple-500/40', text: 'text-purple-200', tag: 'bg-purple-500/20 text-purple-300' },
  { bg: 'bg-teal-950/80', border: 'border-teal-500/40', text: 'text-teal-200', tag: 'bg-teal-500/20 text-teal-300' },
];

export interface StudentCourse {
  course: ParsedCourse;
  selectedGroupCode: string;
  colorIndex: number;
}

export interface CourseDocument {
  id: string;
  courseCode: string;
  title: string;
  category: 'جزوه' | 'نمونه سوال' | 'پروژه' | 'اسلاید';
  notes: string;
  date: string;
}

export interface UserSettings {
  studentName: string;
  studentId: string;
  universityName: string;
  major: string;
  degree: string;
  maxCreditsLimit: number; // 14, 20, 24
  showThursday: boolean;
  defaultViewMode: 'daily' | 'grid';
  enableConflictAlert: boolean;
}

const DEFAULT_SETTINGS: UserSettings = {
  studentName: '',
  studentId: '',
  universityName: '',
  major: '',
  degree: 'کارشناسی',
  maxCreditsLimit: 20,
  showThursday: true,
  defaultViewMode: 'daily',
  enableConflictAlert: true
};

export default function App() {
  // Navigation tabs: mobile bottom bar
  const [activeScreen, setActiveScreen] = useState<'courses' | 'schedule' | 'upload' | 'engine' | 'more'>('courses');
  
  // Search & input
  const [courseCodeQuery, setCourseCodeQuery] = useState('');
  
  // Timetable view mode on mobile (daily cards vs full table)
  const [scheduleViewMode, setScheduleViewMode] = useState<'daily' | 'grid'>('daily');
  const [selectedDayIdx, setSelectedDayIdx] = useState(0);

  // Strategy for smart engine
  const [scheduleEngineStrategy, setScheduleEngineStrategy] = useState<'balance' | 'min_gap' | 'compact' | 'no_8am'>('min_gap');

  // Modals & feedback
  const [isSettingsOpen, setIsSettingsOpen] = useState(false);
  const [settingsActiveTab, setSettingsActiveTab] = useState<'profile' | 'schedule' | 'backup' | 'about' | 'danger'>('profile');
  const [isNewDocOpen, setIsNewDocOpen] = useState(false);
  const [copiedBackup, setCopiedBackup] = useState(false);
  const [uploadedFileName, setUploadedFileName] = useState<string | null>(null);
  const [extractSuccessMsg, setExtractSuccessMsg] = useState<string | null>(null);
  const [settingsToast, setSettingsToast] = useState<string | null>(null);

  // User Settings State (Persisted)
  const [settings, setSettings] = useState<UserSettings>(() => {
    try {
      const saved = localStorage.getItem('cp_user_settings');
      if (saved) return { ...DEFAULT_SETTINGS, ...JSON.parse(saved) };
    } catch {}
    return DEFAULT_SETTINGS;
  });

  // New document form state
  const [newDocCourse, setNewDocCourse] = useState('');
  const [newDocTitle, setNewDocTitle] = useState('');
  const [newDocCategory, setNewDocCategory] = useState<'جزوه' | 'نمونه سوال' | 'پروژه' | 'اسلاید'>('جزوه');
  const [newDocNotes, setNewDocNotes] = useState('');

  // University Catalog (Starts 100% empty and clean for user's real file)
  const [portalHtml, setPortalHtml] = useState<string>(() => {
    try {
      const saved = localStorage.getItem('cp_portal_html');
      if (!saved) return '';
      if (saved.includes('10014') && saved.includes('مديريت پروژه هاي فناوري اطلاعات')) {
        localStorage.removeItem('cp_portal_html');
        return '';
      }
      return saved;
    } catch {
      return '';
    }
  });

  // Student's Enrolled / Planned Courses (Clean by default)
  const [studentCourses, setStudentCourses] = useState<StudentCourse[]>(() => {
    try {
      const saved = localStorage.getItem('cp_student_courses');
      if (saved) return JSON.parse(saved);
    } catch {
      // ignore
    }
    return [];
  });

  // Course Documents & Notes (Clean by default)
  const [documents, setDocuments] = useState<CourseDocument[]>(() => {
    try {
      const saved = localStorage.getItem('cp_documents');
      if (saved) return JSON.parse(saved);
    } catch {
      // ignore
    }
    return [];
  });

  // Save student courses
  useEffect(() => {
    localStorage.setItem('cp_student_courses', JSON.stringify(studentCourses));
  }, [studentCourses]);

  // Save documents
  useEffect(() => {
    localStorage.setItem('cp_documents', JSON.stringify(documents));
  }, [documents]);

  // Save settings
  useEffect(() => {
    localStorage.setItem('cp_user_settings', JSON.stringify(settings));
  }, [settings]);

  // Save portal source
  const handleUpdatePortalSource = (html: string) => {
    setPortalHtml(html);
    localStorage.setItem('cp_portal_html', html);
  };

  // Parsed Catalog
  const universityCatalog = useMemo(() => {
    if (!portalHtml.trim()) return [];
    return parseUniversityHtml(portalHtml);
  }, [portalHtml]);

  // Filtered days based on user settings (e.g. show Thursday or not)
  const activeDaysOfWeek = useMemo(() => {
    if (!settings.showThursday) {
      return DAYS_OF_WEEK.filter(d => d.index !== 5);
    }
    return DAYS_OF_WEEK;
  }, [settings.showThursday]);

  // Search Results for Quick Add
  const searchMatches = useMemo(() => {
    const q = courseCodeQuery.trim().toLowerCase();
    if (!q) return [];
    return universityCatalog.filter(c => 
      c.courseCode.includes(q) ||
      c.name.toLowerCase().includes(q) ||
      c.offerings.some(o => o.instructor.toLowerCase().includes(q))
    ).slice(0, 6);
  }, [courseCodeQuery, universityCatalog]);

  // Total credits
  const totalCredits = useMemo(() => {
    return studentCourses.reduce((sum, item) => sum + (item.course.credits || 0), 0);
  }, [studentCourses]);

  // Check if exceeds limit
  const isCreditOverLimit = totalCredits > settings.maxCreditsLimit;

  // Add course to student's plan
  const handleAddCourseToPlan = (course: ParsedCourse, groupCode?: string) => {
    const targetGroup = groupCode || course.offerings[0]?.groupCode || '1';
    const existingIndex = studentCourses.findIndex(sc => sc.course.courseCode === course.courseCode);
    
    if (existingIndex >= 0) {
      const updated = [...studentCourses];
      updated[existingIndex].selectedGroupCode = targetGroup;
      setStudentCourses(updated);
    } else {
      setStudentCourses(prev => [
        ...prev,
        {
          course,
          selectedGroupCode: targetGroup,
          colorIndex: prev.length % COURSE_COLOR_SCHEMES.length
        }
      ]);
    }
    setCourseCodeQuery('');
  };

  const handleRemoveCourse = (courseCode: string) => {
    setStudentCourses(prev => prev.filter(sc => sc.course.courseCode !== courseCode));
  };

  const handleGroupChange = (courseCode: string, newGroup: string) => {
    setStudentCourses(prev => prev.map(sc => {
      if (sc.course.courseCode === courseCode) {
        return { ...sc, selectedGroupCode: newGroup };
      }
      return sc;
    }));
  };

  // Process uploaded HTML file
  const handleFileUpload = (file: File) => {
    const reader = new FileReader();
    reader.onload = (event) => {
      const content = event.target?.result as string;
      if (content) {
        handleUpdatePortalSource(content);
        setUploadedFileName(file.name);
        const parsed = parseUniversityHtml(content);
        setExtractSuccessMsg(`${parsed.length} درس ترم با موفقیت از فایل ${file.name} استخراج شدند!`);
        setTimeout(() => setExtractSuccessMsg(null), 4000);
      }
    };
    reader.readAsText(file);
  };

  // Import Backup JSON
  const handleImportBackupFile = (file: File) => {
    const reader = new FileReader();
    reader.onload = (event) => {
      try {
        const text = event.target?.result as string;
        const data = JSON.parse(text);
        if (data.studentCourses && Array.isArray(data.studentCourses)) {
          setStudentCourses(data.studentCourses);
        }
        if (data.settings) {
          setSettings(prev => ({ ...prev, ...data.settings }));
        }
        if (data.documents && Array.isArray(data.documents)) {
          setDocuments(data.documents);
        }
        setSettingsToast('فایل پشتیبان با موفقیت بازیابی شد!');
        setTimeout(() => setSettingsToast(null), 3000);
      } catch (e) {
        alert('فایل پشتیبان نامعتبر است.');
      }
    };
    reader.readAsText(file);
  };

  // Add custom document
  const handleAddDocument = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newDocTitle.trim()) return;

    const newDoc: CourseDocument = {
      id: Date.now().toString(),
      courseCode: newDocCourse.trim() || 'عمومی',
      title: newDocTitle.trim(),
      category: newDocCategory,
      notes: newDocNotes.trim(),
      date: new Date().toLocaleDateString('fa-IR')
    };

    setDocuments(prev => [newDoc, ...prev]);
    setNewDocTitle('');
    setNewDocCourse('');
    setNewDocNotes('');
    setIsNewDocOpen(false);
  };

  const handleDeleteDocument = (id: string) => {
    setDocuments(prev => prev.filter(d => d.id !== id));
  };

  // Time conflicts calculation
  const conflicts = useMemo(() => {
    if (!settings.enableConflictAlert) return [];
    const list: string[] = [];
    for (let i = 0; i < studentCourses.length; i++) {
      for (let j = i + 1; j < studentCourses.length; j++) {
        const item1 = studentCourses[i];
        const item2 = studentCourses[j];

        const off1 = item1.course.offerings.find(o => o.groupCode === item1.selectedGroupCode);
        const off2 = item2.course.offerings.find(o => o.groupCode === item2.selectedGroupCode);

        if (!off1 || !off2) continue;

        for (const s1 of off1.sessions) {
          for (const s2 of off2.sessions) {
            if (s1.dayOfWeek === s2.dayOfWeek) {
              const overlapParity = 
                s1.weekType === 'WEEKLY' || 
                s2.weekType === 'WEEKLY' || 
                s1.weekType === s2.weekType;

              if (overlapParity) {
                const [startH1, startM1] = s1.startTime.split(':').map(Number);
                const [endH1, endM1] = s1.endTime.split(':').map(Number);
                const [startH2, startM2] = s2.startTime.split(':').map(Number);
                const [endH2, endM2] = s2.endTime.split(':').map(Number);

                const t1Start = startH1 * 60 + startM1;
                const t1End = endH1 * 60 + endM1;
                const t2Start = startH2 * 60 + startM2;
                const t2End = endH2 * 60 + endM2;

                if (Math.max(t1Start, t2Start) < Math.min(t1End, t2End)) {
                  list.push(
                    `هم‌پوشانی بین «${item1.course.name}» و «${item2.course.name}» در روز ${s1.day} ساعت ${s1.startTime} تا ${s1.endTime}`
                  );
                }
              }
            }
          }
        }
      }
    }
    return list;
  }, [studentCourses, settings.enableConflictAlert]);

  // Clean Backup Export
  const backupJsonString = useMemo(() => {
    const payload = {
      version: '1.4.0',
      versionCode: 6,
      exportDate: new Date().toISOString(),
      settings,
      studentCourses,
      documents,
      roomFormat: convertToRoomDatabaseFormat(studentCourses.map(sc => sc.course))
    };
    return JSON.stringify(payload, null, 2);
  }, [studentCourses, settings, documents]);

  const handleDownloadBackup = () => {
    const blob = new Blob([backupJsonString], { type: 'application/json;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `CoursePlanner_Backup_${settings.studentId || 'std'}_${new Date().toISOString().slice(0, 10)}.json`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  };

  const handleCopyBackup = () => {
    navigator.clipboard.writeText(backupJsonString);
    setCopiedBackup(true);
    setTimeout(() => setCopiedBackup(false), 2000);
  };

  // Sessions for selected day in Mobile Daily view
  const currentDaySessions = useMemo(() => {
    const list: {
      courseName: string;
      courseCode: string;
      groupCode: string;
      instructor: string;
      location: string;
      startTime: string;
      endTime: string;
      weekType: string;
      color: typeof COURSE_COLOR_SCHEMES[0];
    }[] = [];

    studentCourses.forEach(entry => {
      const off = entry.course.offerings.find(o => o.groupCode === entry.selectedGroupCode);
      if (off) {
        off.sessions.forEach(sess => {
          if (sess.dayOfWeek === selectedDayIdx) {
            list.push({
              courseName: entry.course.name,
              courseCode: entry.course.courseCode,
              groupCode: entry.selectedGroupCode,
              instructor: off.instructor,
              location: sess.location,
              startTime: sess.startTime,
              endTime: sess.endTime,
              weekType: sess.weekType,
              color: COURSE_COLOR_SCHEMES[entry.colorIndex]
            });
          }
        });
      }
    });

    return list.sort((a, b) => a.startTime.localeCompare(b.startTime));
  }, [studentCourses, selectedDayIdx]);

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col font-sans pb-20 selection:bg-indigo-500 selection:text-white" dir="rtl">
      {/* Mobile-Friendly Top Bar */}
      <header className="sticky top-0 z-30 bg-slate-900/90 backdrop-blur border-b border-slate-800/80 px-4 py-3">
        <div className="max-w-2xl mx-auto flex items-center justify-between">
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-xl bg-gradient-to-tr from-indigo-600 via-sky-500 to-emerald-400 flex items-center justify-center shadow-md shadow-indigo-600/30">
              <Calendar className="w-4 h-4 text-white" />
            </div>
            <div>
              <div className="flex items-center gap-1.5">
                <span className="font-bold text-white text-sm">
                  {settings.studentName ? `برنامه ${settings.studentName}` : 'Course Planner'}
                </span>
                <span className="text-[10px] bg-indigo-950/80 text-indigo-300 font-mono px-1.5 py-0.2 rounded border border-indigo-700/50">
                  v1.4.0
                </span>
              </div>
              <p className="text-[10px] text-slate-400">
                {settings.universityName ? `${settings.universityName} · ${settings.major || 'دانشجو'}` : 'سامانه انتخاب واحد و برنامه دانشگاه'}
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2">
            {/* Units badge with limit indicator */}
            <div 
              className={`px-2.5 py-1 rounded-lg border text-xs flex items-center gap-1 transition ${
                isCreditOverLimit 
                  ? 'bg-rose-950/80 border-rose-600 text-rose-300 shadow-md shadow-rose-950' 
                  : 'bg-slate-800/80 border-slate-700 text-indigo-300'
              }`}
              title={`سقف مجاز: ${settings.maxCreditsLimit} واحد`}
            >
              <span className="text-slate-400 text-[11px]">واحد:</span>
              <span className="font-mono font-bold text-white">{totalCredits}</span>
              <span className="text-slate-400 text-[10px]">/{settings.maxCreditsLimit}</span>
            </div>

            {/* Settings button */}
            <button
              onClick={() => setIsSettingsOpen(true)}
              className="p-1.5 text-slate-400 hover:text-white rounded-lg hover:bg-slate-800 transition cursor-pointer relative"
              title="تنظیمات پیشرفته"
            >
              <SettingsIcon className="w-4 h-4" />
            </button>
          </div>
        </div>
      </header>

      {/* Credit limit warning toast */}
      {isCreditOverLimit && (
        <div className="max-w-2xl mx-auto w-full px-4 pt-2">
          <div className="p-2.5 bg-rose-950/70 border border-rose-500/50 rounded-xl text-rose-200 text-xs flex items-center justify-between gap-2 shadow-sm">
            <div className="flex items-center gap-2">
              <AlertTriangle className="w-4 h-4 text-rose-400 shrink-0" />
              <span>مجموع واحدهای انتخابی ({totalCredits}) از سقف مجاز شما ({settings.maxCreditsLimit} واحد) فراتر رفته است!</span>
            </div>
            <button
              onClick={() => {
                setSettingsActiveTab('profile');
                setIsSettingsOpen(true);
              }}
              className="text-[10px] bg-rose-800 text-white font-bold px-2 py-0.5 rounded cursor-pointer shrink-0"
            >
              تغییر سقف
            </button>
          </div>
        </div>
      )}

      {/* Main Content Area */}
      <main className="flex-1 max-w-2xl w-full mx-auto px-4 py-4 space-y-4">
        {/* ================= TAB 1: انتخاب واحد (COURSES) ================= */}
        {activeScreen === 'courses' && (
          <div className="space-y-4">
            {universityCatalog.length === 0 ? (
              /* Completely Clean Onboarding State */
              <div className="bg-slate-900 border border-slate-800 rounded-3xl p-6 text-center space-y-4 shadow-xl">
                <div className="w-14 h-14 rounded-2xl bg-indigo-500/10 border border-indigo-500/20 text-indigo-400 flex items-center justify-center mx-auto shadow-inner">
                  <FileUp className="w-7 h-7" />
                </div>
                <div className="space-y-1.5">
                  <h3 className="text-sm font-bold text-white">سامانه آماده استفاده است</h3>
                  <p className="text-xs text-slate-400 max-w-sm mx-auto leading-relaxed">
                    برای شروع، فایل HTML دروس ارائه شده ترم را از پرتال دانشگاه دریافت کرده و در بخش بارگذاری وارد نمایید.
                  </p>
                </div>
                <button
                  onClick={() => setActiveScreen('upload')}
                  className="inline-flex items-center gap-2 bg-indigo-600 hover:bg-indigo-500 text-white font-bold text-xs px-5 py-2.5 rounded-xl shadow-lg shadow-indigo-950 transition cursor-pointer"
                >
                  <Upload className="w-4 h-4" />
                  <span>بارگذاری فایل دروس پرتال</span>
                </button>
              </div>
            ) : (
              /* Quick Add Search Bar */
              <div className="bg-slate-900 border border-slate-800 rounded-2xl p-4 shadow-lg space-y-3">
                <div className="flex items-center justify-between">
                  <h2 className="text-sm font-bold text-white flex items-center gap-2">
                    <Search className="w-4 h-4 text-indigo-400" />
                    <span>افزودن سریع درس با کد</span>
                  </h2>
                  <span className="text-[11px] text-slate-400 font-mono">
                    {universityCatalog.length} درس در بانک پرتال
                  </span>
                </div>

                {/* Input Box */}
                <div className="relative">
                  <input
                    type="text"
                    value={courseCodeQuery}
                    onChange={(e) => setCourseCodeQuery(e.target.value)}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter' && searchMatches.length > 0) {
                        handleAddCourseToPlan(searchMatches[0]);
                      }
                    }}
                    placeholder="کد درس (مثلاً 10014) یا نام درس..."
                    className="w-full bg-slate-950 border border-slate-700 rounded-xl pr-3 pl-20 py-2.5 text-xs text-white placeholder-slate-500 focus:outline-none focus:border-indigo-500 font-mono"
                  />
                  {courseCodeQuery && (
                    <button
                      onClick={() => setCourseCodeQuery('')}
                      className="absolute left-14 top-2 text-slate-400 hover:text-slate-200 p-1 cursor-pointer"
                    >
                      <X className="w-3.5 h-3.5" />
                    </button>
                  )}
                  {searchMatches.length > 0 && (
                    <button
                      onClick={() => handleAddCourseToPlan(searchMatches[0])}
                      className="absolute left-1.5 top-1.5 bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-semibold px-2.5 py-1.5 rounded-lg transition flex items-center gap-1 shadow-md shadow-indigo-950 cursor-pointer"
                    >
                      <Plus className="w-3.5 h-3.5" />
                      <span>افزودن</span>
                    </button>
                  )}
                </div>

                {/* Instant Search Suggestions Dropdown */}
                {courseCodeQuery && searchMatches.length > 0 && (
                  <div className="bg-slate-950 border border-slate-700 rounded-xl shadow-xl divide-y divide-slate-800 overflow-hidden">
                    {searchMatches.map(course => {
                      const isAdded = studentCourses.some(sc => sc.course.courseCode === course.courseCode);
                      return (
                        <div key={course.courseCode} className="p-3 flex items-center justify-between gap-2 hover:bg-slate-900/60 transition">
                          <div className="min-w-0">
                            <div className="flex items-center gap-1.5">
                              <span className="font-mono text-[11px] text-indigo-400 bg-indigo-950 px-1.5 py-0.2 rounded border border-indigo-800/40">
                                {course.courseCode}
                              </span>
                              <span className="font-bold text-white text-xs truncate">{course.name}</span>
                              <span className="text-[11px] text-slate-400">· {course.credits} واحد</span>
                            </div>
                            <div className="text-[11px] text-slate-400 mt-1 truncate">
                              استاد: {course.offerings[0]?.instructor || 'نامشخص'}
                            </div>
                          </div>

                          <div className="flex items-center gap-1 shrink-0">
                            {course.offerings.map(o => (
                              <button
                                key={o.groupCode}
                                onClick={() => handleAddCourseToPlan(course, o.groupCode)}
                                className={`text-[11px] px-2 py-1 rounded-lg font-medium transition cursor-pointer ${
                                  isAdded && studentCourses.find(sc => sc.course.courseCode === course.courseCode)?.selectedGroupCode === o.groupCode
                                    ? 'bg-emerald-600 text-white'
                                    : 'bg-slate-800 hover:bg-indigo-600 text-slate-200 border border-slate-700'
                                }`}
                              >
                                گروه {o.groupCode}
                              </button>
                            ))}
                          </div>
                        </div>
                      );
                    })}
                  </div>
                )}
              </div>
            )}

            {/* Time Conflict Banner */}
            {conflicts.length > 0 && (
              <div className="p-3.5 bg-rose-950/40 border border-rose-500/40 rounded-2xl flex items-start gap-2.5">
                <AlertTriangle className="w-4 h-4 text-rose-400 shrink-0 mt-0.5" />
                <div className="space-y-1">
                  <div className="text-xs font-bold text-rose-300">هشدار تداخل ساعت کلاسی</div>
                  <ul className="text-[11px] text-rose-200/90 space-y-1 list-disc list-inside">
                    {conflicts.map((c, i) => (
                      <li key={i}>{c}</li>
                    ))}
                  </ul>
                </div>
              </div>
            )}

            {/* Selected Courses Section */}
            <div className="space-y-3">
              <div className="flex items-center justify-between">
                <h3 className="text-xs font-bold text-slate-300">
                  دروس انتخابی ترم ({studentCourses.length} درس)
                </h3>
                {studentCourses.length > 0 && (
                  <button
                    onClick={() => setStudentCourses([])}
                    className="text-[11px] text-slate-400 hover:text-rose-400 transition cursor-pointer"
                  >
                    پاکسازی همه
                  </button>
                )}
              </div>

              {studentCourses.length === 0 ? (
                <div className="bg-slate-900/40 border border-dashed border-slate-800 rounded-2xl p-8 text-center space-y-2">
                  <div className="w-10 h-10 rounded-full bg-slate-800/80 flex items-center justify-center mx-auto text-slate-500">
                    <BookOpen className="w-5 h-5" />
                  </div>
                  <h4 className="text-xs font-semibold text-slate-300">هنوز درسی انتخاب نشده است</h4>
                  <p className="text-[11px] text-slate-500 max-w-xs mx-auto">
                    از فیلد جستجوی بالا کد درس مورد نظرتان را وارد و گروه دلخواه را انتخاب کنید.
                  </p>
                </div>
              ) : (
                <div className="space-y-2.5">
                  {studentCourses.map((entry) => {
                    const currentOffering = entry.course.offerings.find(o => o.groupCode === entry.selectedGroupCode) 
                      || entry.course.offerings[0];
                    const colorScheme = COURSE_COLOR_SCHEMES[entry.colorIndex];

                    return (
                      <div
                        key={entry.course.courseCode}
                        className={`bg-slate-900 border ${colorScheme.border} rounded-2xl p-3.5 space-y-2.5 shadow-sm`}
                      >
                        {/* Title & Delete */}
                        <div className="flex items-start justify-between gap-2">
                          <div className="min-w-0">
                            <div className="flex items-center gap-1.5">
                              <span className="font-mono text-[11px] text-indigo-400 bg-slate-950 px-1.5 py-0.2 rounded border border-slate-800">
                                {entry.course.courseCode}
                              </span>
                              <h4 className="font-bold text-white text-xs truncate">{entry.course.name}</h4>
                            </div>
                            <div className="text-[11px] text-slate-400 mt-0.5">
                              {entry.course.credits} واحد · {entry.course.department}
                            </div>
                          </div>

                          <button
                            onClick={() => handleRemoveCourse(entry.course.courseCode)}
                            className="text-slate-500 hover:text-rose-400 p-1 cursor-pointer"
                            title="حذف"
                          >
                            <Trash2 className="w-3.5 h-3.5" />
                          </button>
                        </div>

                        {/* Group and Instructor Selector */}
                        <div className="bg-slate-950/70 px-2.5 py-1.5 rounded-xl border border-slate-800/80 flex items-center justify-between text-xs">
                          <span className="text-[11px] text-slate-300">
                            استاد: <strong className="text-white font-medium">{currentOffering.instructor}</strong>
                          </span>

                          {entry.course.offerings.length > 1 ? (
                            <select
                              value={entry.selectedGroupCode}
                              onChange={(e) => handleGroupChange(entry.course.courseCode, e.target.value)}
                              className="bg-slate-900 border border-slate-700 text-[11px] text-indigo-300 font-bold rounded-lg px-2 py-0.5 focus:outline-none"
                            >
                              {entry.course.offerings.map(o => (
                                <option key={o.groupCode} value={o.groupCode}>
                                  گروه {o.groupCode}
                                </option>
                              ))}
                            </select>
                          ) : (
                            <span className="text-[11px] font-bold text-indigo-300 bg-indigo-950/60 px-2 py-0.5 rounded border border-indigo-800/40">
                              گروه {currentOffering.groupCode}
                            </span>
                          )}
                        </div>

                        {/* Class Sessions */}
                        <div className="space-y-1">
                          {currentOffering.sessions.map((sess, idx) => (
                            <div
                              key={idx}
                              className="flex items-center justify-between text-[11px] bg-slate-950/40 px-2 py-1 rounded-lg text-slate-300"
                            >
                              <div className="flex items-center gap-1.5">
                                <Clock className="w-3 h-3 text-indigo-400" />
                                <span className="font-bold text-white">{sess.day}</span>
                                <span className="font-mono text-slate-300">{sess.startTime} - {sess.endTime}</span>
                              </div>
                              <div className="flex items-center gap-1.5 text-slate-400">
                                {sess.weekType !== 'WEEKLY' && (
                                  <span className="text-[10px] text-sky-300 bg-sky-950 px-1 py-0.2 rounded border border-sky-800">
                                    {sess.weekType === 'EVEN' ? 'زوج' : 'فرد'}
                                  </span>
                                )}
                                <span>{sess.location}</span>
                              </div>
                            </div>
                          ))}
                        </div>

                        {/* Exam Date */}
                        {currentOffering.exam && currentOffering.exam.time && (
                          <div className="text-[10px] text-slate-400 pt-1 border-t border-slate-800/60 flex items-center justify-between">
                            <span>امتحان: ساعت {currentOffering.exam.time}</span>
                            {currentOffering.exam.date && <span>{currentOffering.exam.date}</span>}
                          </div>
                        )}
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          </div>
        )}

        {/* ================= TAB 2: برنامه هفتگی (SCHEDULE) ================= */}
        {activeScreen === 'schedule' && (
          <div className="space-y-4">
            {/* View Mode Switcher Header */}
            <div className="bg-slate-900 border border-slate-800 rounded-2xl p-3 flex items-center justify-between">
              <div>
                <h2 className="text-xs font-bold text-white">برنامه کلاسی هفتگی</h2>
                <p className="text-[10px] text-slate-400 mt-0.5">{studentCourses.length} درس انتخاب‌شده</p>
              </div>

              {/* Segmented Control */}
              <div className="flex items-center bg-slate-950 p-1 rounded-xl border border-slate-800 text-xs">
                <button
                  onClick={() => setScheduleViewMode('daily')}
                  className={`px-3 py-1 rounded-lg text-[11px] font-medium transition cursor-pointer flex items-center gap-1 ${
                    scheduleViewMode === 'daily' ? 'bg-indigo-600 text-white' : 'text-slate-400'
                  }`}
                >
                  <List className="w-3.5 h-3.5" />
                  <span>روزانه</span>
                </button>
                <button
                  onClick={() => setScheduleViewMode('grid')}
                  className={`px-3 py-1 rounded-lg text-[11px] font-medium transition cursor-pointer flex items-center gap-1 ${
                    scheduleViewMode === 'grid' ? 'bg-indigo-600 text-white' : 'text-slate-400'
                  }`}
                >
                  <LayoutGrid className="w-3.5 h-3.5" />
                  <span>جدول کامل</span>
                </button>
              </div>
            </div>

            {/* MODE A: Mobile Daily Cards */}
            {scheduleViewMode === 'daily' && (
              <div className="space-y-3">
                {/* Horizontal Day Tabs */}
                <div className="grid grid-cols-6 gap-1 bg-slate-900 p-1.5 rounded-2xl border border-slate-800">
                  {activeDaysOfWeek.map(({ name, index }) => {
                    const isSelected = selectedDayIdx === index;
                    let classCount = 0;
                    studentCourses.forEach(sc => {
                      const off = sc.course.offerings.find(o => o.groupCode === sc.selectedGroupCode);
                      if (off?.sessions.some(s => s.dayOfWeek === index)) classCount++;
                    });

                    return (
                      <button
                        key={index}
                        onClick={() => setSelectedDayIdx(index)}
                        className={`py-2 px-1 rounded-xl text-center transition cursor-pointer flex flex-col items-center justify-center ${
                          isSelected 
                            ? 'bg-indigo-600 text-white shadow-md' 
                            : 'text-slate-400 hover:text-slate-200'
                        }`}
                      >
                        <span className="text-[11px] font-bold">{name}</span>
                        {classCount > 0 && (
                          <span className={`text-[9px] font-mono px-1 rounded-full mt-0.5 ${
                            isSelected ? 'bg-indigo-950 text-indigo-200' : 'bg-slate-800 text-indigo-400'
                          }`}>
                            {classCount}
                          </span>
                        )}
                      </button>
                    );
                  })}
                </div>

                {/* Day Sessions List */}
                <div className="space-y-2.5">
                  <div className="text-xs font-bold text-slate-300 px-1">
                    کلاس‌های {DAYS_OF_WEEK[selectedDayIdx]?.name || 'انتخاب‌شده'} ({currentDaySessions.length} جلسه)
                  </div>

                  {currentDaySessions.length === 0 ? (
                    <div className="bg-slate-900/50 border border-slate-800/80 rounded-2xl p-8 text-center space-y-1">
                      <div className="text-slate-500 text-xs">در این روز کلاسی ثبت نشده است</div>
                      <p className="text-[11px] text-slate-600">می‌توانید روز دیگری را انتخاب کنید</p>
                    </div>
                  ) : (
                    currentDaySessions.map((item, idx) => (
                      <div
                        key={idx}
                        className={`bg-slate-900 border ${item.color.border} rounded-2xl p-3.5 space-y-2 shadow-sm`}
                      >
                        <div className="flex items-start justify-between gap-2">
                          <div>
                            <div className="text-xs font-bold text-white">{item.courseName}</div>
                            <div className="text-[11px] text-slate-400 mt-0.5">
                              گروه {item.groupCode} · استاد {item.instructor}
                            </div>
                          </div>
                          <span className={`text-[10px] font-mono px-2 py-0.5 rounded-full font-bold ${item.color.tag}`}>
                            {item.startTime} - {item.endTime}
                          </span>
                        </div>

                        <div className="flex items-center justify-between text-[11px] text-slate-400 pt-1 border-t border-slate-800/60">
                          <span>مکان: <strong className="text-slate-300 font-medium">{item.location}</strong></span>
                          {item.weekType !== 'WEEKLY' && (
                            <span className="text-[10px] text-sky-400 font-bold">
                              {item.weekType === 'EVEN' ? 'هفته زوج' : 'هفته فرد'}
                            </span>
                          )}
                        </div>
                      </div>
                    ))
                  )}
                </div>
              </div>
            )}

            {/* MODE B: Full Grid */}
            {scheduleViewMode === 'grid' && (
              <div className="bg-slate-900 border border-slate-800 rounded-2xl overflow-hidden shadow-xl">
                <div className="overflow-x-auto">
                  <table className="w-full text-center border-collapse min-w-[700px]">
                    <thead>
                      <tr className="bg-slate-800/80 border-b border-slate-700 text-xs font-semibold text-slate-300">
                        <th className="p-2 border-l border-slate-700/80 w-16">روز</th>
                        {TIME_SLOTS.map((time) => (
                          <th key={time} className="p-1 border-l border-slate-700/60 font-mono text-slate-400 text-[11px]">
                            {time}
                          </th>
                        ))}
                      </tr>
                    </thead>
                    <tbody>
                      {activeDaysOfWeek.map(({ name: dayName, index: dayIdx }) => {
                        const dayItems: { entry: StudentCourse; session: any; offering: CourseOffering }[] = [];
                        studentCourses.forEach(entry => {
                          const off = entry.course.offerings.find(o => o.groupCode === entry.selectedGroupCode);
                          if (off) {
                            off.sessions.forEach(s => {
                              if (s.dayOfWeek === dayIdx) {
                                dayItems.push({ entry, session: s, offering: off });
                              }
                            });
                          }
                        });

                        return (
                          <tr key={dayIdx} className="border-b border-slate-800">
                            <td className="p-2 border-l border-slate-800 bg-slate-900/90 font-bold text-xs text-indigo-300">
                              {dayName}
                            </td>
                            {TIME_SLOTS.map((time) => {
                              const [slotHour] = time.split(':').map(Number);
                              const active = dayItems.filter(({ session }) => {
                                const [sHour] = session.startTime.split(':').map(Number);
                                const [eHour] = session.endTime.split(':').map(Number);
                                return slotHour >= sHour && slotHour < eHour;
                              });

                              return (
                                <td key={time} className="p-1 border-l border-slate-800/60 h-16 align-top">
                                  {active.map(({ entry, session }, idx) => {
                                    const color = COURSE_COLOR_SCHEMES[entry.colorIndex];
                                    return (
                                      <div
                                        key={idx}
                                        className={`p-1 rounded ${color.bg} border ${color.border} text-white text-[10px] leading-tight mb-1 text-right`}
                                      >
                                        <div className="font-bold truncate">{entry.course.name}</div>
                                        <div className="text-[9px] text-slate-400 truncate">{session.location}</div>
                                      </div>
                                    );
                                  })}
                                </td>
                              );
                            })}
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              </div>
            )}
          </div>
        )}

        {/* ================= TAB 3: بارگذاری فایل پرتال (UPLOAD) ================= */}
        {activeScreen === 'upload' && (
          <div className="space-y-4">
            {/* Success Toast */}
            {extractSuccessMsg && (
              <div className="p-3 bg-emerald-950/90 border border-emerald-500/60 rounded-xl text-emerald-200 text-xs flex items-center justify-between gap-2 shadow-lg">
                <div className="flex items-center gap-2">
                  <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0" />
                  <span className="font-medium text-[11px]">{extractSuccessMsg}</span>
                </div>
                <button
                  onClick={() => setActiveScreen('courses')}
                  className="bg-emerald-600 text-white text-[10px] font-bold px-2.5 py-1 rounded-lg transition cursor-pointer"
                >
                  مشاهده دروس
                </button>
              </div>
            )}

            {/* Direct Upload Card */}
            <div className="bg-slate-900 border border-slate-800 rounded-2xl p-4 shadow-xl space-y-4">
              <div className="flex items-start justify-between gap-2">
                <div>
                  <h3 className="text-xs font-bold text-white flex items-center gap-2">
                    <FileUp className="w-4 h-4 text-indigo-400" />
                    <span>بارگذاری فایل دروس پرتال دانشگاه</span>
                  </h3>
                  <p className="text-[11px] text-slate-400 mt-0.5">
                    فایل HTML ذخیره‌شده از پرتال را اینجا قرار دهید تا همه دروس خودکار اضافه شوند
                  </p>
                </div>
                {universityCatalog.length > 0 && (
                  <span className="text-[10px] font-mono text-emerald-400 bg-emerald-950/80 border border-emerald-800/60 px-2 py-0.5 rounded">
                    {universityCatalog.length} درس فعال
                  </span>
                )}
              </div>

              {/* Big File Drop / Select Zone */}
              <label className="border-2 border-dashed border-slate-700 hover:border-indigo-500 rounded-2xl p-6 flex flex-col items-center justify-center gap-2.5 bg-slate-950/70 cursor-pointer transition text-center group">
                <input
                  type="file"
                  accept=".html,.htm"
                  className="hidden"
                  onChange={(e) => {
                    const file = e.target.files?.[0];
                    if (file) handleFileUpload(file);
                  }}
                />
                <div className="w-12 h-12 rounded-2xl bg-indigo-500/10 text-indigo-400 flex items-center justify-center group-hover:scale-110 transition shadow-inner">
                  <Upload className="w-6 h-6" />
                </div>
                <div>
                  <span className="text-xs font-bold text-indigo-300 group-hover:underline">
                    لمس کنید تا فایل HTML پرتال را انتخاب کنید
                  </span>
                  <span className="text-[11px] text-slate-500 block mt-1">
                    (پشتیبانی از فایل‌های .html و .htm سامانه‌های پویا، گلستان، سما و...)
                  </span>
                </div>
                {uploadedFileName && (
                  <div className="mt-1 text-[11px] text-emerald-400 bg-emerald-950/60 px-2.5 py-0.5 rounded-full border border-emerald-800/50">
                    آخرین فایل لودشده: {uploadedFileName}
                  </div>
                )}
              </label>

              {/* 3 Step Visual Guide */}
              <div className="bg-slate-950 p-3.5 rounded-2xl border border-slate-800 space-y-2">
                <div className="text-[11px] font-bold text-slate-300">
                  راهنمای سریع (۳ مرحله ساده):
                </div>
                <div className="space-y-1.5 text-[11px] text-slate-400">
                  <div className="flex items-center gap-2">
                    <span className="w-4 h-4 rounded-full bg-slate-800 text-indigo-300 font-mono text-[10px] flex items-center justify-center font-bold">1</span>
                    <span>وارد پرتال دانشگاه شده و به صفحه «دروس ارائه شده» بروید.</span>
                  </div>
                  <div className="flex items-center gap-2">
                    <span className="w-4 h-4 rounded-full bg-slate-800 text-indigo-300 font-mono text-[10px] flex items-center justify-center font-bold">2</span>
                    <span>صفحه را در گوشی یا سیستم خود ذخیره کنید (Save as HTML).</span>
                  </div>
                  <div className="flex items-center gap-2">
                    <span className="w-4 h-4 rounded-full bg-slate-800 text-indigo-300 font-mono text-[10px] flex items-center justify-center font-bold">3</span>
                    <span>فایل را در کادر بالا انتخاب کنید؛ همه دروس درجا اضافه می‌شوند!</span>
                  </div>
                </div>
              </div>

              {/* Clear Catalog button */}
              {universityCatalog.length > 0 && (
                <div className="flex items-center justify-between pt-1 border-t border-slate-800/80">
                  <span className="text-[11px] text-slate-500">می‌خواهید چارت را پاکسازی کنید؟</span>
                  <button
                    onClick={() => {
                      handleUpdatePortalSource('');
                      setUploadedFileName(null);
                      setExtractSuccessMsg('بانک دروس پاکسازی شد.');
                      setTimeout(() => setExtractSuccessMsg(null), 2500);
                    }}
                    className="flex items-center gap-1 text-[11px] text-rose-400 hover:text-rose-300 transition cursor-pointer"
                  >
                    <RotateCcw className="w-3 h-3" />
                    <span>پاکسازی بانک دروس</span>
                  </button>
                </div>
              )}
            </div>
          </div>
        )}

        {/* ================= TAB 4: چیدمان هوشمند (SMART ENGINE) ================= */}
        {activeScreen === 'engine' && (
          <div className="space-y-4">
            <div className="bg-slate-900 border border-slate-800 p-4 rounded-2xl space-y-3">
              <div className="flex items-center gap-2.5">
                <div className="p-2 rounded-xl bg-indigo-500/10 text-indigo-400 border border-indigo-500/20">
                  <Sparkles className="w-4 h-4" />
                </div>
                <div>
                  <h3 className="text-xs font-bold text-white">موتور چیدمان خودکار و بدون تداخل</h3>
                  <p className="text-[10px] text-slate-400">بهترین ترکیب گروه‌ها را با استراتژی مدنظرتان بیابید</p>
                </div>
              </div>

              {/* 4 Strategy Cards */}
              <div className="grid grid-cols-2 gap-2 pt-1">
                {[
                  { id: 'balance', title: '⚖️ توازن کلی', desc: 'پخش یکنواخت در روزها' },
                  { id: 'min_gap', title: '⏱️ حداقل گپ', desc: 'کاهش زمان‌های خالی' },
                  { id: 'compact', title: '📦 روزهای فشرده', desc: 'کلاس در کمترین روز' },
                  { id: 'no_8am', title: '☀️ بدون ۸ صبح', desc: 'حذف کلاس‌های اول وقت' }
                ].map(strat => (
                  <button
                    key={strat.id}
                    onClick={() => setScheduleEngineStrategy(strat.id as any)}
                    className={`p-3 rounded-xl border text-right transition cursor-pointer ${
                      scheduleEngineStrategy === strat.id
                        ? 'bg-indigo-950/60 border-indigo-500 text-white'
                        : 'bg-slate-950/60 border-slate-800 text-slate-400'
                    }`}
                  >
                    <div className="font-bold text-xs text-slate-200">{strat.title}</div>
                    <div className="text-[10px] text-slate-400 mt-0.5">{strat.desc}</div>
                  </button>
                ))}
              </div>

              <button
                onClick={() => setActiveScreen('schedule')}
                className="w-full bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-bold py-2 px-3 rounded-xl transition flex items-center justify-center gap-1.5 shadow-md shadow-indigo-950 cursor-pointer"
              >
                <span>مشاهده چیدمان در برنامه هفتگی</span>
                <ChevronLeft className="w-3.5 h-3.5" />
              </button>
            </div>
          </div>
        )}

        {/* ================= TAB 5: پشتیبان و جزوات (MORE) ================= */}
        {activeScreen === 'more' && (
          <div className="space-y-4">
            {/* Backup Section */}
            <div className="bg-slate-900 border border-slate-800 p-4 rounded-2xl space-y-3">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <Database className="w-4 h-4 text-indigo-400" />
                  <h3 className="text-xs font-bold text-white">پشتیبان‌گیری از برنامه</h3>
                </div>
                <span className="text-[10px] text-emerald-400 bg-emerald-950/60 border border-emerald-800/40 px-2 py-0.5 rounded">
                  ذخیره محلی و امن
                </span>
              </div>

              <p className="text-[11px] text-slate-400 leading-relaxed">
                برنامه کلاسی و دروس انتخابی خود را ذخیره کنید یا فایل پشتیبان را با هم‌دانشگاهی‌هایتان به اشتراک بگذارید:
              </p>

              <div className="grid grid-cols-2 gap-2 pt-1">
                <button
                  onClick={handleDownloadBackup}
                  className="flex items-center justify-center gap-1.5 bg-indigo-600 hover:bg-indigo-500 text-white font-medium py-2 px-3 rounded-xl text-xs shadow-md transition cursor-pointer"
                >
                  <Download className="w-3.5 h-3.5" />
                  <span>دانلود فایل پشتیبان</span>
                </button>

                <button
                  onClick={handleCopyBackup}
                  className="flex items-center justify-center gap-1.5 bg-slate-800 hover:bg-slate-700 text-slate-200 font-medium py-2 px-3 rounded-xl border border-slate-700 text-xs transition cursor-pointer"
                >
                  {copiedBackup ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
                  <span>{copiedBackup ? 'کپی شد!' : 'کپی اطلاعات'}</span>
                </button>
              </div>
            </div>

            {/* Course Documents Section */}
            <div className="space-y-2.5">
              <div className="flex items-center justify-between px-1">
                <h3 className="text-xs font-bold text-slate-300 flex items-center gap-1.5">
                  <FileText className="w-3.5 h-3.5 text-indigo-400" />
                  <span>آرشیو جزوات و یادداشت‌ها ({documents.length})</span>
                </h3>
                <button
                  onClick={() => setIsNewDocOpen(true)}
                  className="inline-flex items-center gap-1 text-[11px] bg-indigo-600 hover:bg-indigo-500 text-white px-2.5 py-1 rounded-lg transition cursor-pointer"
                >
                  <Plus className="w-3 h-3" />
                  <span>افزودن یادداشت</span>
                </button>
              </div>

              {documents.length === 0 ? (
                <div className="bg-slate-900/50 border border-slate-800 rounded-2xl p-6 text-center space-y-1">
                  <div className="text-slate-400 text-xs font-medium">هیچ یادداشت یا جزوه‌ای ثبت نشده است</div>
                  <p className="text-[11px] text-slate-600">می‌توانید با زدن دکمه «افزودن یادداشت» جزوات خود را ثبت کنید</p>
                </div>
              ) : (
                documents.map(doc => (
                  <div key={doc.id} className="bg-slate-900 border border-slate-800 p-3 rounded-2xl space-y-1.5">
                    <div className="flex items-center justify-between">
                      <span className="font-mono text-[10px] text-indigo-300 bg-slate-950 px-1.5 py-0.2 rounded border border-slate-800">
                        کد {doc.courseCode}
                      </span>
                      <div className="flex items-center gap-2">
                        <span className="text-[10px] text-emerald-400 bg-emerald-950 px-1.5 py-0.2 rounded border border-emerald-800">
                          {doc.category}
                        </span>
                        <button
                          onClick={() => handleDeleteDocument(doc.id)}
                          className="text-slate-500 hover:text-rose-400 p-0.5 cursor-pointer"
                        >
                          <Trash2 className="w-3 h-3" />
                        </button>
                      </div>
                    </div>
                    <div className="text-xs font-bold text-white">{doc.title}</div>
                    {doc.notes && <div className="text-[11px] text-slate-400">{doc.notes}</div>}
                    <div className="text-[10px] text-slate-600 font-mono">{doc.date}</div>
                  </div>
                ))
              )}
            </div>
          </div>
        )}
      </main>

      {/* Mobile Bottom Navigation Bar (Thumb Friendly) */}
      <nav className="fixed bottom-0 left-0 right-0 z-40 bg-slate-900/95 backdrop-blur border-t border-slate-800/80 px-2 py-1.5">
        <div className="max-w-md mx-auto grid grid-cols-5 gap-1">
          <button
            onClick={() => setActiveScreen('courses')}
            className={`flex flex-col items-center py-1 rounded-xl transition cursor-pointer ${
              activeScreen === 'courses' ? 'text-indigo-400' : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            <BookOpen className="w-4 h-4" />
            <span className="text-[10px] mt-1 font-medium">انتخاب واحد</span>
          </button>

          <button
            onClick={() => setActiveScreen('schedule')}
            className={`flex flex-col items-center py-1 rounded-xl transition cursor-pointer ${
              activeScreen === 'schedule' ? 'text-indigo-400' : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            <Calendar className="w-4 h-4" />
            <span className="text-[10px] mt-1 font-medium">برنامه هفتگی</span>
          </button>

          <button
            onClick={() => setActiveScreen('upload')}
            className={`flex flex-col items-center py-1 rounded-xl transition cursor-pointer ${
              activeScreen === 'upload' ? 'text-indigo-400' : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            <Upload className="w-4 h-4" />
            <span className="text-[10px] mt-1 font-medium">بارگذاری فایل</span>
          </button>

          <button
            onClick={() => setActiveScreen('engine')}
            className={`flex flex-col items-center py-1 rounded-xl transition cursor-pointer ${
              activeScreen === 'engine' ? 'text-indigo-400' : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            <Sparkles className="w-4 h-4" />
            <span className="text-[10px] mt-1 font-medium">هوشمند</span>
          </button>

          <button
            onClick={() => setActiveScreen('more')}
            className={`flex flex-col items-center py-1 rounded-xl transition cursor-pointer ${
              activeScreen === 'more' ? 'text-indigo-400' : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            <Layers className="w-4 h-4" />
            <span className="text-[10px] mt-1 font-medium">جزوات و ذخیره</span>
          </button>
        </div>
      </nav>

      {/* Modal: Add New Document */}
      {isNewDocOpen && (
        <div className="fixed inset-0 z-50 bg-black/80 backdrop-blur-sm flex items-end sm:items-center justify-center p-0 sm:p-4">
          <form
            onSubmit={handleAddDocument}
            className="bg-slate-900 border border-slate-800 rounded-t-3xl sm:rounded-2xl max-w-lg w-full p-5 space-y-3.5 shadow-2xl"
          >
            <div className="flex items-center justify-between">
              <h3 className="text-sm font-bold text-white flex items-center gap-2">
                <FileText className="w-4 h-4 text-indigo-400" />
                <span>ثبت جزوه یا یادداشت جدید</span>
              </h3>
              <button
                type="button"
                onClick={() => setIsNewDocOpen(false)}
                className="text-slate-400 hover:text-white p-1 cursor-pointer"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <div className="space-y-2.5 text-xs">
              <div>
                <label className="text-slate-300 font-medium block mb-1">عنوان یادداشت / جزوه</label>
                <input
                  type="text"
                  required
                  value={newDocTitle}
                  onChange={(e) => setNewDocTitle(e.target.value)}
                  placeholder="مثال: خلاصه مباحث میان‌ترم..."
                  className="w-full bg-slate-950 border border-slate-700 rounded-xl px-3 py-2 text-white placeholder-slate-500 focus:outline-none focus:border-indigo-500"
                />
              </div>

              <div className="grid grid-cols-2 gap-2">
                <div>
                  <label className="text-slate-300 font-medium block mb-1">کد درس (اختیاری)</label>
                  <input
                    type="text"
                    value={newDocCourse}
                    onChange={(e) => setNewDocCourse(e.target.value)}
                    placeholder="مثال: 10559"
                    className="w-full bg-slate-950 border border-slate-700 rounded-xl px-3 py-2 text-white font-mono placeholder-slate-500 focus:outline-none focus:border-indigo-500"
                  />
                </div>
                <div>
                  <label className="text-slate-300 font-medium block mb-1">دسته‌بندی</label>
                  <select
                    value={newDocCategory}
                    onChange={(e) => setNewDocCategory(e.target.value as any)}
                    className="w-full bg-slate-950 border border-slate-700 rounded-xl px-3 py-2 text-white focus:outline-none focus:border-indigo-500"
                  >
                    <option value="جزوه">جزوه</option>
                    <option value="نمونه سوال">نمونه سوال</option>
                    <option value="اسلاید">اسلاید</option>
                    <option value="پروژه">پروژه</option>
                  </select>
                </div>
              </div>

              <div>
                <label className="text-slate-300 font-medium block mb-1">توضیحات و نکات</label>
                <textarea
                  value={newDocNotes}
                  onChange={(e) => setNewDocNotes(e.target.value)}
                  placeholder="نکات مهم و مباحث کلیدی..."
                  className="w-full h-20 bg-slate-950 border border-slate-700 rounded-xl px-3 py-2 text-white placeholder-slate-500 focus:outline-none focus:border-indigo-500 resize-none"
                />
              </div>

              <button
                type="submit"
                className="w-full bg-indigo-600 hover:bg-indigo-500 text-white font-bold py-2.5 rounded-xl text-xs transition cursor-pointer mt-1 shadow-md shadow-indigo-950"
              >
                ثبت و ذخیره یادداشت
              </button>
            </div>
          </form>
        </div>
      )}

      {/* ================= COMPREHENSIVE SETTINGS MODAL ================= */}
      {isSettingsOpen && (
        <div className="fixed inset-0 z-50 bg-black/85 backdrop-blur-sm flex items-end sm:items-center justify-center p-0 sm:p-4">
          <div className="bg-slate-900 border border-slate-800 rounded-t-3xl sm:rounded-2xl max-w-lg w-full max-h-[92vh] flex flex-col shadow-2xl overflow-hidden">
            {/* Settings Header */}
            <div className="p-4 border-b border-slate-800 flex items-center justify-between bg-slate-950/70">
              <div className="flex items-center gap-2">
                <div className="p-1.5 rounded-xl bg-indigo-500/10 text-indigo-400 border border-indigo-500/20">
                  <SettingsIcon className="w-4 h-4" />
                </div>
                <div>
                  <h3 className="text-sm font-bold text-white">تنظیمات برنامه</h3>
                  <p className="text-[10px] text-slate-400">مشخصات دانشجو، سقف واحدها و نحوه نمایش</p>
                </div>
              </div>
              <button
                onClick={() => setIsSettingsOpen(false)}
                className="text-slate-400 hover:text-white p-1 rounded-lg hover:bg-slate-800 transition cursor-pointer"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            {/* Settings Toast */}
            {settingsToast && (
              <div className="bg-emerald-950 border-b border-emerald-800 px-4 py-2 text-emerald-300 text-xs flex items-center gap-1.5 animate-fadeIn">
                <CheckCircle2 className="w-3.5 h-3.5" />
                <span>{settingsToast}</span>
              </div>
            )}

            {/* Settings Tab Navigation */}
            <div className="flex items-center border-b border-slate-800 bg-slate-950 px-2 overflow-x-auto scrollbar-none text-xs">
              <button
                onClick={() => setSettingsActiveTab('profile')}
                className={`py-2.5 px-3 border-b-2 font-medium shrink-0 flex items-center gap-1.5 transition cursor-pointer ${
                  settingsActiveTab === 'profile'
                    ? 'border-indigo-500 text-indigo-400'
                    : 'border-transparent text-slate-400 hover:text-slate-200'
                }`}
              >
                <User className="w-3.5 h-3.5" />
                <span>مشخصات دانشجو</span>
              </button>

              <button
                onClick={() => setSettingsActiveTab('schedule')}
                className={`py-2.5 px-3 border-b-2 font-medium shrink-0 flex items-center gap-1.5 transition cursor-pointer ${
                  settingsActiveTab === 'schedule'
                    ? 'border-indigo-500 text-indigo-400'
                    : 'border-transparent text-slate-400 hover:text-slate-200'
                }`}
              >
                <Sliders className="w-3.5 h-3.5" />
                <span>تقویم و نمایش</span>
              </button>

              <button
                onClick={() => setSettingsActiveTab('backup')}
                className={`py-2.5 px-3 border-b-2 font-medium shrink-0 flex items-center gap-1.5 transition cursor-pointer ${
                  settingsActiveTab === 'backup'
                    ? 'border-indigo-500 text-indigo-400'
                    : 'border-transparent text-slate-400 hover:text-slate-200'
                }`}
              >
                <Database className="w-3.5 h-3.5" />
                <span>پشتیبان‌گیری</span>
              </button>

              <button
                onClick={() => setSettingsActiveTab('about')}
                className={`py-2.5 px-3 border-b-2 font-medium shrink-0 flex items-center gap-1.5 transition cursor-pointer ${
                  settingsActiveTab === 'about'
                    ? 'border-indigo-500 text-indigo-400'
                    : 'border-transparent text-slate-400 hover:text-slate-200'
                }`}
              >
                <Info className="w-3.5 h-3.5" />
                <span>درباره برنامه</span>
              </button>

              <button
                onClick={() => setSettingsActiveTab('danger')}
                className={`py-2.5 px-3 border-b-2 font-medium shrink-0 flex items-center gap-1.5 transition cursor-pointer ${
                  settingsActiveTab === 'danger'
                    ? 'border-rose-500 text-rose-400'
                    : 'border-transparent text-slate-400 hover:text-slate-200'
                }`}
              >
                <ShieldAlert className="w-3.5 h-3.5" />
                <span>پاکسازی</span>
              </button>
            </div>

            {/* Settings Body */}
            <div className="p-4 space-y-4 overflow-y-auto flex-1 text-xs">
              {/* TAB 1: مشخصات دانشجو */}
              {settingsActiveTab === 'profile' && (
                <div className="space-y-3.5">
                  <div className="grid grid-cols-2 gap-2.5">
                    <div>
                      <label className="text-slate-300 font-medium block mb-1">نام و نام‌خانوادگی</label>
                      <input
                        type="text"
                        value={settings.studentName}
                        onChange={(e) => setSettings({ ...settings, studentName: e.target.value })}
                        placeholder="مثال: علیرضا محمدی"
                        className="w-full bg-slate-950 border border-slate-700 rounded-xl px-3 py-2 text-white placeholder-slate-500 focus:outline-none focus:border-indigo-500"
                      />
                    </div>
                    <div>
                      <label className="text-slate-300 font-medium block mb-1">شماره دانشجویی</label>
                      <input
                        type="text"
                        value={settings.studentId}
                        onChange={(e) => setSettings({ ...settings, studentId: e.target.value })}
                        placeholder="مثال: 40112345"
                        className="w-full bg-slate-950 border border-slate-700 rounded-xl px-3 py-2 text-white font-mono placeholder-slate-500 focus:outline-none focus:border-indigo-500"
                      />
                    </div>
                  </div>

                  <div className="grid grid-cols-2 gap-2.5">
                    <div>
                      <label className="text-slate-300 font-medium block mb-1">نام دانشگاه / موسسه</label>
                      <input
                        type="text"
                        value={settings.universityName}
                        onChange={(e) => setSettings({ ...settings, universityName: e.target.value })}
                        placeholder="مثال: دانشگاه خراسان"
                        className="w-full bg-slate-950 border border-slate-700 rounded-xl px-3 py-2 text-white placeholder-slate-500 focus:outline-none focus:border-indigo-500"
                      />
                    </div>
                    <div>
                      <label className="text-slate-300 font-medium block mb-1">رشته تحصیلی</label>
                      <input
                        type="text"
                        value={settings.major}
                        onChange={(e) => setSettings({ ...settings, major: e.target.value })}
                        placeholder="مثال: مهندسی کامپیوتر"
                        className="w-full bg-slate-950 border border-slate-700 rounded-xl px-3 py-2 text-white placeholder-slate-500 focus:outline-none focus:border-indigo-500"
                      />
                    </div>
                  </div>

                  {/* Academic Credit Rules */}
                  <div className="bg-slate-950 p-3.5 rounded-2xl border border-slate-800 space-y-2.5">
                    <div className="flex items-center justify-between">
                      <label className="font-bold text-slate-200 block">
                        سقف مجاز انتخاب واحد ترم:
                      </label>
                      <span className="font-mono font-bold text-indigo-400 bg-indigo-950 px-2 py-0.5 rounded border border-indigo-800/60">
                        {settings.maxCreditsLimit} واحد
                      </span>
                    </div>

                    <div className="grid grid-cols-3 gap-2">
                      {[
                        { limit: 14, label: 'مشروط (۱۴ واحد)', desc: 'معدل زیر ۱۲' },
                        { limit: 20, label: 'عادی (۲۰ واحد)', desc: 'معدل ۱۲ تا ۱۶.۹۹' },
                        { limit: 24, label: 'الف / ترم آخر (۲۴ واحد)', desc: 'معدل ۱۷+ یا فراغت' }
                      ].map(rule => (
                        <button
                          key={rule.limit}
                          type="button"
                          onClick={() => setSettings({ ...settings, maxCreditsLimit: rule.limit })}
                          className={`p-2 rounded-xl border text-center transition cursor-pointer ${
                            settings.maxCreditsLimit === rule.limit
                              ? 'bg-indigo-950 border-indigo-500 text-white shadow'
                              : 'bg-slate-900 border-slate-800 text-slate-400 hover:text-slate-200'
                          }`}
                        >
                          <div className="font-bold text-[11px]">{rule.label}</div>
                          <div className="text-[9px] text-slate-500 mt-0.5">{rule.desc}</div>
                        </button>
                      ))}
                    </div>
                    <p className="text-[10px] text-slate-400 leading-relaxed">
                      💡 اگر مجموع واحدهای شما از سقف انتخاب‌شده فراتر رود، برنامه به صورت خودکار هشدار می‌دهد.
                    </p>
                  </div>
                </div>
              )}

              {/* TAB 2: تقویم و نمایش */}
              {settingsActiveTab === 'schedule' && (
                <div className="space-y-3.5">
                  <div className="bg-slate-950 p-3.5 rounded-2xl border border-slate-800 space-y-3">
                    <div className="flex items-center justify-between">
                      <div>
                        <span className="font-bold text-slate-200 block text-xs">نمایش پنج‌شنبه در جدول و برنامه</span>
                        <span className="text-[10px] text-slate-400">اگر در روز پنج‌شنبه کلاس ندارید، می‌توانید آن را مخفی کنید</span>
                      </div>
                      <input
                        type="checkbox"
                        checked={settings.showThursday}
                        onChange={(e) => setSettings({ ...settings, showThursday: e.target.checked })}
                        className="w-4 h-4 accent-indigo-600 rounded cursor-pointer"
                      />
                    </div>

                    <div className="border-t border-slate-800 pt-3 flex items-center justify-between">
                      <div>
                        <span className="font-bold text-slate-200 block text-xs">هشدار هوشمند تداخل کلاسی</span>
                        <span className="text-[10px] text-slate-400">بررسی خودکار هم‌پوشانی ساعت کلاس‌ها و روزهای زوج/فرد</span>
                      </div>
                      <input
                        type="checkbox"
                        checked={settings.enableConflictAlert}
                        onChange={(e) => setSettings({ ...settings, enableConflictAlert: e.target.checked })}
                        className="w-4 h-4 accent-indigo-600 rounded cursor-pointer"
                      />
                    </div>
                  </div>

                  <div className="bg-slate-950 p-3.5 rounded-2xl border border-slate-800 space-y-2">
                    <span className="font-bold text-slate-200 block text-xs">حالت پیش‌فرض برنامه کلاسی:</span>
                    <div className="grid grid-cols-2 gap-2">
                      <button
                        type="button"
                        onClick={() => {
                          setSettings({ ...settings, defaultViewMode: 'daily' });
                          setScheduleViewMode('daily');
                        }}
                        className={`p-2.5 rounded-xl border text-center transition cursor-pointer flex items-center justify-center gap-1.5 ${
                          settings.defaultViewMode === 'daily'
                            ? 'bg-indigo-950 border-indigo-500 text-white font-bold'
                            : 'bg-slate-900 border-slate-800 text-slate-400'
                        }`}
                      >
                        <List className="w-3.5 h-3.5" />
                        <span>نمای کارتی روزانه</span>
                      </button>
                      <button
                        type="button"
                        onClick={() => {
                          setSettings({ ...settings, defaultViewMode: 'grid' });
                          setScheduleViewMode('grid');
                        }}
                        className={`p-2.5 rounded-xl border text-center transition cursor-pointer flex items-center justify-center gap-1.5 ${
                          settings.defaultViewMode === 'grid'
                            ? 'bg-indigo-950 border-indigo-500 text-white font-bold'
                            : 'bg-slate-900 border-slate-800 text-slate-400'
                        }`}
                      >
                        <LayoutGrid className="w-3.5 h-3.5" />
                        <span>جدول هفتگی کامل</span>
                      </button>
                    </div>
                  </div>
                </div>
              )}

              {/* TAB 3: پشتیبان و بازیابی */}
              {settingsActiveTab === 'backup' && (
                <div className="space-y-3.5">
                  <div className="bg-slate-950 p-3.5 rounded-2xl border border-slate-800 space-y-3">
                    <div>
                      <h4 className="font-bold text-slate-200 text-xs">خروجی کامل داده‌ها (Export)</h4>
                      <p className="text-[10px] text-slate-400 mt-0.5">
                        از انتخاب واحد، برنامه هفتگی و یادداشت‌های خود فایل خروجی JSON بگیرید
                      </p>
                    </div>
                    <div className="grid grid-cols-2 gap-2">
                      <button
                        onClick={handleDownloadBackup}
                        className="bg-indigo-600 hover:bg-indigo-500 text-white font-bold py-2 px-3 rounded-xl transition flex items-center justify-center gap-1.5 cursor-pointer shadow-md"
                      >
                        <Download className="w-3.5 h-3.5" />
                        <span>دانلود فایل JSON</span>
                      </button>
                      <button
                        onClick={handleCopyBackup}
                        className="bg-slate-800 hover:bg-slate-700 text-slate-200 font-bold py-2 px-3 rounded-xl border border-slate-700 transition flex items-center justify-center gap-1.5 cursor-pointer"
                      >
                        {copiedBackup ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
                        <span>{copiedBackup ? 'کپی شد' : 'کپی در کلیپ‌بورد'}</span>
                      </button>
                    </div>
                  </div>

                  {/* Import Backup */}
                  <div className="bg-slate-950 p-3.5 rounded-2xl border border-slate-800 space-y-3">
                    <div>
                      <h4 className="font-bold text-slate-200 text-xs">بازیابی فایل پشتیبان (Import)</h4>
                      <p className="text-[10px] text-slate-400 mt-0.5">
                        فایل JSON قبلی خود یا فایل پشتیبان یک هم‌دانشگاهی را وارد کنید:
                      </p>
                    </div>
                    <label className="border border-dashed border-slate-700 hover:border-indigo-500 rounded-xl p-3.5 flex items-center justify-center gap-2 bg-slate-900 cursor-pointer transition">
                      <input
                        type="file"
                        accept=".json"
                        className="hidden"
                        onChange={(e) => {
                          const file = e.target.files?.[0];
                          if (file) handleImportBackupFile(file);
                        }}
                      />
                      <FolderDown className="w-4 h-4 text-indigo-400" />
                      <span className="font-bold text-indigo-300 text-xs">انتخاب فایل پشتیبان JSON</span>
                    </label>
                  </div>
                </div>
              )}

              {/* TAB 4: درباره برنامه */}
              {settingsActiveTab === 'about' && (
                <div className="space-y-3.5">
                  <div className="bg-slate-950 p-4 rounded-2xl border border-slate-800 space-y-3">
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-2">
                        <GraduationCap className="w-4 h-4 text-indigo-400" />
                        <span className="font-bold text-white text-xs">برنامه‌ریز و انتخاب واحد دانشگاه</span>
                      </div>
                      <span className="text-[10px] text-indigo-300 bg-indigo-950 px-2 py-0.5 rounded border border-indigo-800">
                        نسخه ۱.۱.۰
                      </span>
                    </div>

                    <p className="text-[11px] text-slate-300 leading-relaxed">
                      این برنامه ویژه دانشجویان طراحی شده تا فرآیند انتخاب واحد، چیدمان هفتگی کلاس‌ها و مدیریت زمان‌بندی را بدون تداخل و با سادگی انجام دهند.
                    </p>

                    <div className="bg-slate-900 p-3 rounded-xl border border-slate-800 text-[11px] space-y-2 text-slate-300">
                      <div className="font-bold text-white text-[11px] mb-1">امکانات ویژه دانشجویان:</div>
                      <div className="flex items-center gap-2 text-slate-300">
                        <Check className="w-3.5 h-3.5 text-emerald-400 shrink-0" />
                        <span>استخراج مستقیم دروس از فایل ذخیره‌شده پرتال دانشگاه</span>
                      </div>
                      <div className="flex items-center gap-2 text-slate-300">
                        <Check className="w-3.5 h-3.5 text-emerald-400 shrink-0" />
                        <span>بررسی خودکار و هوشمند تداخل ساعات کلاسی</span>
                      </div>
                      <div className="flex items-center gap-2 text-slate-300">
                        <Check className="w-3.5 h-3.5 text-emerald-400 shrink-0" />
                        <span>کنترل سقف مجاز انتخاب واحد (مشروط، عادی، الف)</span>
                      </div>
                      <div className="flex items-center gap-2 text-slate-300">
                        <Check className="w-3.5 h-3.5 text-emerald-400 shrink-0" />
                        <span>دفترچه ثبت یادداشت، جزوات و نمونه‌سوالات</span>
                      </div>
                    </div>

                    <div className="pt-1">
                      <a
                        href="https://github.com/Ara-0x/Course-Planner"
                        target="_blank"
                        rel="noreferrer"
                        className="w-full bg-slate-900 hover:bg-slate-800 text-slate-200 border border-slate-700 font-medium py-2 px-3 rounded-xl transition flex items-center justify-center gap-1.5 text-xs"
                      >
                        <ExternalLink className="w-3.5 h-3.5" />
                        <span>مشاهده سورس‌کد در گیت‌هاب (Ara-0x/Course-Planner)</span>
                      </a>
                    </div>
                  </div>
                </div>
              )}

              {/* TAB 5: پاکسازی و ریست */}
              {settingsActiveTab === 'danger' && (
                <div className="space-y-3">
                  <div className="p-3 bg-amber-950/40 border border-amber-600/40 rounded-xl text-amber-200 text-xs flex items-center gap-2">
                    <Info className="w-4 h-4 text-amber-400 shrink-0" />
                    <span>عملیات‌های این بخش به صورت تفکیک‌شده و با دقت بالا انجام می‌شوند.</span>
                  </div>

                  {/* Selective Clears */}
                  <div className="bg-slate-950 p-3 rounded-xl border border-slate-800 flex items-center justify-between">
                    <div>
                      <span className="font-bold text-slate-200 block text-xs">پاکسازی دروس انتخاب‌شده</span>
                      <span className="text-[10px] text-slate-400">حذف دروس انتخابی و بازنشانی برنامه هفتگی (چارت دانشگاه حفظ می‌شود)</span>
                    </div>
                    <button
                      onClick={() => {
                        if (confirm('آیا از پاکسازی دروس انتخاب‌شده مطمئن هستید؟')) {
                          setStudentCourses([]);
                          setSettingsToast('دروس انتخابی با موفقیت پاک شدند.');
                          setTimeout(() => setSettingsToast(null), 2500);
                        }
                      }}
                      className="bg-slate-800 hover:bg-rose-900/60 text-rose-300 px-3 py-1.5 rounded-lg border border-slate-700 text-xs font-bold transition cursor-pointer shrink-0"
                    >
                      پاکسازی دروس
                    </button>
                  </div>

                  <div className="bg-slate-950 p-3 rounded-xl border border-slate-800 flex items-center justify-between">
                    <div>
                      <span className="font-bold text-slate-200 block text-xs">پاکسازی بانک دروس دانشگاه</span>
                      <span className="text-[10px] text-slate-400">حذف چارت دروس لودشده از پرتال</span>
                    </div>
                    <button
                      onClick={() => {
                        if (confirm('آیا از حذف بانک دروس دانشگاه مطمئن هستید؟')) {
                          handleUpdatePortalSource('');
                          setUploadedFileName(null);
                          setSettingsToast('بانک دروس با موفقیت پاک شد.');
                          setTimeout(() => setSettingsToast(null), 2500);
                        }
                      }}
                      className="bg-slate-800 hover:bg-rose-900/60 text-rose-300 px-3 py-1.5 rounded-lg border border-slate-700 text-xs font-bold transition cursor-pointer shrink-0"
                    >
                      پاکسازی کاتالوگ
                    </button>
                  </div>

                  <div className="bg-slate-950 p-3 rounded-xl border border-slate-800 flex items-center justify-between">
                    <div>
                      <span className="font-bold text-slate-200 block text-xs">پاکسازی یادداشت‌ها و جزوات</span>
                      <span className="text-[10px] text-slate-400">حذف تمامی یادداشت‌ها و جزوات ثبت‌شده ({documents.length} مورد)</span>
                    </div>
                    <button
                      onClick={() => {
                        if (confirm('آیا از حذف تمامی یادداشت‌ها و جزوات مطمئن هستید؟')) {
                          setDocuments([]);
                          setSettingsToast('یادداشت‌ها پاک شدند.');
                          setTimeout(() => setSettingsToast(null), 2500);
                        }
                      }}
                      className="bg-slate-800 hover:bg-rose-900/60 text-rose-300 px-3 py-1.5 rounded-lg border border-slate-700 text-xs font-bold transition cursor-pointer shrink-0"
                    >
                      پاکسازی جزوات
                    </button>
                  </div>

                  {/* Master Factory Reset */}
                  <div className="bg-rose-950/30 p-3.5 rounded-xl border border-rose-800/60 space-y-2 mt-4">
                    <span className="font-bold text-rose-200 block text-xs">بازنشانی کامل به تنظیمات کارخانه (Factory Reset)</span>
                    <p className="text-[10px] text-rose-300/80 leading-relaxed">
                      کلیه اطلاعات، تنظیمات پروفایل دانشجو، بانک دروس، یادداشت‌ها و کش محلی به طور کامل پاک می‌شوند.
                    </p>
                    <button
                      onClick={() => {
                        if (confirm('هشدار: کلیه داده‌های شما حذف خواهند شد. آیا مطمئن هستید؟')) {
                          try {
                            localStorage.clear();
                          } catch {}
                          setPortalHtml('');
                          setStudentCourses([]);
                          setDocuments([]);
                          setSettings(DEFAULT_SETTINGS);
                          setUploadedFileName(null);
                          setIsSettingsOpen(false);
                        }
                      }}
                      className="w-full bg-rose-700 hover:bg-rose-600 text-white font-bold py-2 rounded-xl text-xs transition cursor-pointer"
                    >
                      بازنشانی کامل کل اطلاعات
                    </button>
                  </div>
                </div>
              )}
            </div>

            {/* Settings Footer */}
            <div className="p-3 border-t border-slate-800 bg-slate-950 flex items-center justify-between">
              <span className="text-[10px] text-slate-500 font-mono">نسخه ۱.۱.۰ · ویژه دانشجویان</span>
              <button
                onClick={() => setIsSettingsOpen(false)}
                className="bg-indigo-600 hover:bg-indigo-500 text-white font-bold text-xs px-4 py-1.5 rounded-xl transition cursor-pointer"
              >
                تأیید و ذخیره
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
