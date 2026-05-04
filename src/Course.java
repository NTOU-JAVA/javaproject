/**
 * Course 表示課表中的一門課程。
 */
public class Course {
    private int    id;
    private String name;        // 課程名稱（必填）
    private String code;        // 課代號
    private String location;    // 教室
    private String professor;   // 教授名稱
    private String department;  // 開課系所
    private String classYear;   // 開課年班
    private int    dayOfWeek;   // 1=週一 … 5=週五
    private int    startPeriod; // 開始節次 1~14
    private int    endPeriod;   // 結束節次 1~14
    private String note;        // 備註

    public Course() {}

    public Course(int id, String name, String code, String location,
                  String professor, int dayOfWeek, int startPeriod, int endPeriod,
                  String note) {
        this.id          = id;
        this.name        = name;
        this.code        = code;
        this.location    = location;
        this.professor   = professor;
        this.department  = "";
        this.classYear   = "";
        this.dayOfWeek   = dayOfWeek;
        this.startPeriod = startPeriod;
        this.endPeriod   = endPeriod;
        this.note        = note;
    }

    public int    getId()                      { return id; }
    public void   setId(int id)                { this.id = id; }

    public String getName()                    { return name != null ? name : ""; }
    public void   setName(String name)         { this.name = name; }

    public String getCode()                    { return code != null ? code : ""; }
    public void   setCode(String code)         { this.code = code; }

    public String getLocation()                { return location != null ? location : ""; }
    public void   setLocation(String location) { this.location = location; }

    public String getProfessor()                       { return professor != null ? professor : ""; }
    public void   setProfessor(String professor)       { this.professor = professor; }

    public String getDepartment()                      { return department != null ? department : ""; }
    public void   setDepartment(String department)     { this.department = department; }

    public String getClassYear()                       { return classYear != null ? classYear : ""; }
    public void   setClassYear(String classYear)       { this.classYear = classYear; }

    public int    getDayOfWeek()               { return dayOfWeek; }
    public void   setDayOfWeek(int dayOfWeek)  { this.dayOfWeek = dayOfWeek; }

    public int    getStartPeriod()                     { return startPeriod; }
    public void   setStartPeriod(int startPeriod)      { this.startPeriod = startPeriod; }

    public int    getEndPeriod()                       { return endPeriod; }
    public void   setEndPeriod(int endPeriod)          { this.endPeriod = endPeriod; }

    public String getNote()                    { return note != null ? note : ""; }
    public void   setNote(String note)         { this.note = note; }
}