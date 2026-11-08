-- Active: 1791927942003@@127.0.0.1@3306@cs410-project
DROP TABLE IF EXISTS Grade;
DROP TABLE IF EXISTS Enrollments;
DROP TABLE IF EXISTS Student;
DROP TABLE IF EXISTS Assignment;
DROP TABLE IF EXISTS Categories;
DROP TABLE IF EXISTS Class;

CREATE TABLE Class (
    courseNum VARCHAR(6) NOT NULL,
    term VARCHAR(4) NOT NULL,
    sectionNum VARCHAR(3) NOT NULL,
    description VARCHAR(32),
    CONSTRAINT PK_Class PRIMARY KEY (courseNum, term, sectionNum)
);

CREATE TABLE Categories (
    courseNum VARCHAR(6) NOT NULL,
    term VARCHAR(4) NOT NULL,
    sectionNum VARCHAR(3) NOT NULL,
    name VARCHAR(50) NOT NULL,
    weight NUMERIC(5, 2) NOT NULL CHECK (weight >= 0),
    CONSTRAINT PK_Categories PRIMARY KEY (courseNum, term, sectionNum, name),
    CONSTRAINT FK_Categories_Class FOREIGN KEY (courseNum, term, sectionNum)
        REFERENCES Class (courseNum, term, sectionNum)
        ON DELETE CASCADE
        ON UPDATE CASCADE
);

CREATE TABLE Assignment (
    courseNum VARCHAR(6) NOT NULL,
    term VARCHAR(4) NOT NULL,
    sectionNum VARCHAR(3) NOT NULL,
    categoryName VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    pointVal NUMERIC(6, 2) NOT NULL CHECK (pointVal >= 0),
    CONSTRAINT PK_Assignment PRIMARY KEY (courseNum, term, sectionNum, categoryName, name),
    CONSTRAINT FK_Assignment_Categories FOREIGN KEY (courseNum, term, sectionNum, categoryName)
        REFERENCES Categories (courseNum, term, sectionNum, name)
        ON DELETE CASCADE
        ON UPDATE CASCADE
);

CREATE TABLE Student (
    studentId INT NOT NULL,
    username VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    CONSTRAINT PK_Student PRIMARY KEY (studentId),
    CONSTRAINT UQ_Student_Username UNIQUE (username)
);

CREATE TABLE Enrollments (
    studentId INT NOT NULL,
    courseNum VARCHAR(6) NOT NULL,
    term VARCHAR(4) NOT NULL,
    sectionNum VARCHAR(3) NOT NULL,
    totalGrade DECIMAL,
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

CREATE TABLE Grade (
    studentId INT NOT NULL,
    courseNum VARCHAR(6) NOT NULL,
    term VARCHAR(4) NOT NULL,
    sectionNum VARCHAR(3) NOT NULL,
    categoryName VARCHAR(50) NOT NULL,
    assignmentName VARCHAR(100) NOT NULL,
    score DECIMAL,
    CONSTRAINT PK_Grade PRIMARY KEY (studentId, courseNum, term, sectionNum, categoryName, assignmentName),
    CONSTRAINT FK_Grade_Assignment FOREIGN KEY (courseNum, term, sectionNum, categoryName, assignmentName)
        REFERENCES Assignment (courseNum, term, sectionNum, categoryName, name)
        ON DELETE CASCADE
        ON UPDATE CASCADE,
    CONSTRAINT FK_Grade_Enrollment FOREIGN KEY (studentId, courseNum, term, sectionNum)
        REFERENCES Enrollments (studentId, courseNum, term, sectionNum)
        ON DELETE CASCADE
        ON UPDATE CASCADE
);

CREATE INDEX idx_student_username ON Student(username);
CREATE INDEX idx_grade_score ON Grade(score);