Feature: System health check
  As a system administrator
  I want to verify the API Gateway is running
  So that I know the system is operational

  Scenario: API Gateway responds
    When I request GET "/health"
    Then the response status should be 200
