Feature: Security and access control
  As an API Gateway
  I want to enforce authentication and authorization
  So that only authorized users can access protected resources

  Background:
    Given I am authenticated as ADMIN

  Scenario: Access denied without token
    When I request GET "/employees" without token
    Then the response status should be 401

  Scenario: Access denied with invalid token
    When I request GET "/employees" with invalid token
    Then the response status should be 401

  Scenario: ADMIN can list employees
    When I request GET "/employees" with my token
    Then the response status should be 200

  Scenario: Non-admin users cannot create employees
    Given I am authenticated as a non-admin user
    When I try to create an employee with name "Test User" and unique email ending with "security.blocked"
    Then the response status should be 401 or 403
