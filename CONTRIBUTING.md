# Contributing to Enterprise Bus Validator System

First off, thank you for considering contributing to the Enterprise Bus Validator System! It's people like you that make such systems robust and highly secure.

## 1. Code of Conduct

By participating in this project, you are expected to uphold standard professional conduct. Be respectful, inclusive, and constructive in your feedback and code reviews.

## 2. How Can I Contribute?

### Reporting Bugs
If you find a bug, please create an Issue and include:
*   **A clear and descriptive title.**
*   **The exact hardware/vendor model** (e.g., Telpo E60, Q6, Z90).
*   **Steps to reproduce the behavior.**
*   **Crash logs / Logcat output** (make sure to redact sensitive info).

### Suggesting Enhancements
Enhancement suggestions are tracked as GitHub Issues. Please provide:
*   A clear use case for the feature.
*   How it benefits the multi-operator or multi-vendor architecture.

### Submitting Pull Requests
1.  **Fork the repo** and create your branch from `main`.
2.  If you've added code that should be tested, **add tests**.
3.  Ensure the test suite passes.
4.  Make sure your code adheres to our **Kotlin Style Guide** and **Clean Architecture** patterns.
5.  If you modify the Hardware Abstraction Layer (HAL), ensure you are not breaking contracts for other vendors.
6.  Issue a Pull Request with a comprehensive description of the changes.

## 3. Architecture Rules
When contributing, please adhere to the following:
*   **No Popups:** The UI must maintain the state-screen architecture. No dialogs or toasts.
*   **Hardware Agnosticism:** Never hardcode vendor-specific logic outside of `core/hardware-drivers/`.
*   **Memory Efficiency:** Avoid object allocations in high-frequency loops (like serial port reading). Use byte array pools.
*   **Thread Safety:** Ensure all DB writes and hardware IO operate on designated Dispatchers.

Thank you for contributing to building a world-class transit payment system!
