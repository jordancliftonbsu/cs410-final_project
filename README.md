# CS410 Group Project
### Members:
- Jordan Clifton
- Aaron Eagleton

## AI Disclaimer:
Generative AI was used in this project.

### Milestone 1 Usage
Generative AI (Google Gemini) was used as a scaffolding tool during milestone 1. 
Specifically, it was consulted to format the initial SQL CREATE TABLE statements 
based on specifications and to generate a boilerplate Java JDBC connection test 
script for verifying database connection. All schema design decisions, 
primary/foreign key relationships, and application logic were reviewed, verified, 
and refined by us.

### Milestone 2 Usage
- Generative AI (Google Gemini - Jordan) was used to create the EnvLoader class. This was
deemed acceptable as the class has nothing to do with the content of the project
(databases) and is only used to load private .env variables in order to protect 
our public github repo from potential abuse.

- Generative AI (Claude - Aaron) was used to help diagnose and fix a problem found in
  milestone 1 schema. It found 3 things, which were loose CHECK constraints,
  a single name column on student instead of lastname / first and an error in
  computing total grade. I also used it to help framework/scaffold for listClasses,
  the error handling loops in main per command, and it wrote the formatting for printTable
  as I did not want to manually format it. 
