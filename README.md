# Poker Game Engine – Design Principles & Future Vision

A scalable Java poker engine built with modern software engineering principles in mind.

---

## Project Goal

This project aims to transform a monolithic poker implementation into a clean, modular, and scalable system that follows industry-standard programming principles and architectural patterns.

---

## Core Programming Principles

### 1. Single Responsibility Principle (SRP)

Each class has exactly one responsibility:

| Class -> Responsibility |
|------------------------|
| PokerTable -> Store game state |
| HandRanker -> Evaluate hands |
| PokerGameEngine -> Control game flow |
| GameFrame -> Render UI |
| Controller -> Handle user actions |

---

### 2. Separation of Concerns

The system is modeled after **MVC** Pattern:

- **Model** – Game data  
- **View** – User interface  
- **Controller** – Input handling  
- **Services** – Hand evaluation, deck logic, etc.

This allows independent development and testing.

---

### 3. Open / Closed Principle

Game rules are extensible:

- New poker variants  
- New ranking systems  
- New AI players  

Without modifying existing engine logic.

---

### 4. Dependency Inversion

High-level modules depend on abstractions:

```java
HandRanker ranker = new TexasHoldemHandRanker();
