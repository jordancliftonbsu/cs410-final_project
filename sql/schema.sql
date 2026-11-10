-- Gradebook Manager schema. Run with:  mysql -u <user> -p < schema.sql
-- NOTE: this script DROPs and recreates every table, so re-running it wipes data.
-- (Reload sample data afterwards from dump.sql once that exists.)

CREATE DATABASE IF NOT EXISTS `cs410-project`;
USE `cs410-project`;

DROP TABLE IF EXISTS Grade;
DROP TABLE IF EXISTS Enrollments;
DROP TABLE IF EXISTS Student;
DROP TABLE IF EXISTS Assignment;
DROP TABLE IF EXISTS Categories;
DROP TABLE IF EXISTS Class;

-- D1: a class is identified by (course number, term, section)
CREATE TABLE Class (
    courseNum VARCHAR(10) NOT NULL,
    term VARCHAR(8) NOT NULL,
    sectionNum VARCHAR(8) NOT NULL,
    description VARCHAR(100),
    CONSTRAINT PK_Class PRIMARY KEY (courseNum, term, sectionNum)
);

-- D2: category names are unique within a class (enforced by the PK)
CREATE TABLE Categories (
    courseNum VARCHAR(10) NOT NULL,
    term VARCHAR(8) NOT NULL,
    sectionNum VARCHAR(8) NOT NULL,
    name VARCHAR(50) NOT NULL,
    weight DECIMAL(5, 2) NOT NULL,
    CONSTRAINT PK_Categories PRIMARY KEY (courseNum, term, sectionNum, name),
    CONSTRAINT CK_Categories_Weight CHECK (weight > 0),
    CONSTRAINT FK_Categories_Class FOREIGN KEY (courseNum, term, sectionNum)
        REFERENCES Class (courseNum, term, sectionNum)
        ON DELETE CASCADE
        ON UPDATE CASCADE
);

-- D3: an assignment belongs to exactly one category of its class.
-- UQ_Assignment_Name makes the assignment name unique per class (not per category).
CREATE TABLE Assignment (
    courseNum VARCHAR(10) NOT NULL,
    term VARCHAR(8) NOT NULL,
    sectionNum VARCHAR(8) NOT NULL,
    categoryName VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    pointVal DECIMAL(6, 2) NOT NULL,
    CONSTRAINT PK_Assignment PRIMARY KEY (courseNum, term, sectionNum, categoryName, name),
    CONSTRAINT UQ_Assignment_Name UNIQUE (courseNum, term, sectionNum, name),
    CONSTRAINT CK_Assignment_Points CHECK (pointVal > 0),
    CONSTRAINT FK_Assignment_Categories FOREIGN KEY (courseNum, term, sectionNum, categoryName)
        REFERENCES Categories (courseNum, term, sectionNum, name)
        ON DELETE CASCADE
        ON UPDATE CASCADE
);

-- D4: username and student ID are each unique across the whole system
CREATE TABLE Student (
    studentId INT NOT NULL,
    username VARCHAR(50) NOT NULL,
    lastName VARCHAR(50) NOT NULL,
    firstName VARCHAR(50) NOT NULL,
    CONSTRAINT PK_Student PRIMARY KEY (studentId),
    CONSTRAINT UQ_Student_Username UNIQUE (username)
);

-- D5: a student cannot be enrolled in the same class twice (enforced by the PK)
CREATE TABLE Enrollments (
    studentId INT NOT NULL,
    courseNum VARCHAR(10) NOT NULL,
    term VARCHAR(8) NOT NULL,
    sectionNum VARCHAR(8) NOT NULL,
    CONSTRAINT PK_Enrollments PRIMARY KEY (studentId, courseNum, term, sectionNum),
    CONSTRAINT FK_Enrollments_Student FOREIGN KEY (studentId)
        REFERENCES Student (studentId)
        ON DELETE CASCADE
        ON UPDATE CASCADE,
    CONSTRAINT FK_Enrollments_Class FOREIGN KEY (courseNum, term, sectionNum)
        REFERENCES Class (courseNum, term, sectionNum)
        ON DELETE CASCADE
        ON UPDATE CASCADE
);

-- D6: one grade per (student, assignment). "Ungraded" is the absence of a row,
-- never a 0 and never a NULL score, so score is NOT NULL.
-- D7: FK_Grade_Enrollment carries the class columns, so a grade can only exist
-- for a student who is enrolled in the class that owns the assignment.
CREATE TABLE Grade (
    studentId INT NOT NULL,
    courseNum VARCHAR(10) NOT NULL,
    term VARCHAR(8) NOT NULL,
    sectionNum VARCHAR(8) NOT NULL,
    categoryName VARCHAR(50) NOT NULL,
    assignmentName VARCHAR(100) NOT NULL,
    score DECIMAL(6, 2) NOT NULL,
    CONSTRAINT PK_Grade PRIMARY KEY (studentId, courseNum, term, sectionNum, categoryName, assignmentName),
    CONSTRAINT CK_Grade_Score CHECK (score >= 0),
    CONSTRAINT FK_Grade_Assignment FOREIGN KEY (courseNum, term, sectionNum, categoryName, assignmentName)
        REFERENCES Assignment (courseNum, term, sectionNum, categoryName, name)
        ON DELETE CASCADE
        ON UPDATE CASCADE,
    CONSTRAINT FK_Grade_Enrollment FOREIGN KEY (studentId, courseNum, term, sectionNum)
        REFERENCES Enrollments (studentId, courseNum, term, sectionNum)
        ON DELETE CASCADE
        ON UPDATE CASCADE
);

-- Index: serves `gradebook` / `show-students` (ORDER BY lastName) and name searches in show-students.
CREATE INDEX idx_student_name ON Student (lastName, firstName);
-- Index: serves `list-classes` (order by term, then course) and select-class lookups by term.
-- (The PK is course-first, so it cannot serve term-first access.)
CREATE INDEX idx_class_term_course ON Class (term, courseNum);
