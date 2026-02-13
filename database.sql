-- 1. Create the Database 
CREATE DATABASE IF NOT EXISTS task_manager_db;
USE task_manager_db;

-- 2. Drop the table if it exists to start fresh
DROP TABLE IF EXISTS tasks;

-- 3. Create the Tasks Table
CREATE TABLE tasks (
    id INT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(100) NOT NULL,
    description TEXT,
    priority ENUM('Low', 'Medium', 'High') DEFAULT 'Medium',
    due_date DATE,
    status ENUM('Pending', 'Completed') DEFAULT 'Pending',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 4. Insert 20 Realistic Tasks
INSERT INTO tasks (title, description, priority, due_date, status) VALUES 
-- HIGH PRIORITY TASKS
('Fix Login Bug', 'Resolve NullPointerException in auth module.', 'High', '2026-02-14', 'Pending'),
('Pay Electricity Bill', 'Bill amount $120. Due before Friday.', 'High', '2026-02-15', 'Pending'),
('Submit Project Report', 'Finalize Q1 financial report for the manager.', 'High', '2026-02-20', 'Pending'),
('Renew Car Insurance', 'Policy expires. Compare quotes immediately.', 'High', '2026-03-01', 'Pending'),
('Dentist Appointment', 'Annual checkup at City Dental Clinic.', 'High', '2026-02-18', 'Pending'),
('Server Maintenance', 'Update production server security patches.', 'High', '2026-02-16', 'Pending'),
('Call Mom', 'Check in on family, it has been a while.', 'High', '2026-02-13', 'Pending'),

-- MEDIUM PRIORITY TASKS
('Buy Groceries', 'Milk, Eggs, Bread, Spinach, and Chicken.', 'Medium', '2026-02-13', 'Pending'),
('Team Meeting', 'Weekly sync to discuss sprint progress.', 'Medium', '2026-02-16', 'Pending'),
('Refactor Codebase', 'Clean up the UserDAO class and add comments.', 'Medium', '2026-02-25', 'Pending'),
('Buy Birthday Gift', 'Get something nice for John’s party on Saturday.', 'Medium', '2026-02-17', 'Pending'),
('Update LinkedIn', 'Add the new Smart Task Manager project to profile.', 'Medium', '2026-02-14', 'Completed'),
('Schedule Interview', 'Reply to the recruiter from Tech Corp.', 'Medium', '2026-02-15', 'Pending'),
('Car Service', 'Oil change and tire rotation.', 'Medium', '2026-02-28', 'Pending'),

-- LOW PRIORITY TASKS
('Gym Workout', 'Leg day: Squats, Lunges, Calf raises.', 'Low', '2026-02-15', 'Pending'),
('Read Java Book', 'Finish Chapter 5 on Polymorphism.', 'Low', '2026-02-28', 'Pending'),
('Clean Home Office', 'Organize desk cables and file old documents.', 'Low', '2026-02-10', 'Pending'), -- Overdue
('Plan Summer Vacation', 'Look for hotels in Bali for June.', 'Low', '2026-06-15', 'Pending'),
('Water Plants', 'Water the indoor peace lily and snake plant.', 'Low', '2026-02-13', 'Completed'),
('Write Blog Post', 'Draft an article about "Learning JDBC".', 'Low', '2026-03-05', 'Pending');