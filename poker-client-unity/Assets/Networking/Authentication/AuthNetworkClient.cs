using System;
using System.Net.Http;
using System.Text;
using System.Threading.Tasks;
using Core.Models;
using Newtonsoft.Json;
using PokerGame.Client.Utils;
using UnityEngine;

namespace Networking.Authentication
{
    public class AuthNetworkClient
    {
        private readonly HttpClient _httpClient = new();
        private const string ServerBaseUrl = "http://localhost:8080/api/auth";

        public async Task<bool> RequestPasswordResetAsync(string username)
        {
            try
            {
                var payload = new { username };
                string jsonBody = JsonConvert.SerializeObject(payload);
                var content = new StringContent(jsonBody, Encoding.UTF8, "application/json");

                HttpResponseMessage response = await _httpClient.PostAsync($"{ServerBaseUrl}/forgot-password", content);
                Debug.Log($"Forgot Password Status Code: {(int)response.StatusCode}");
                return response.IsSuccessStatusCode;
            }
            catch (Exception e)
            {
                Debug.LogError($"Password reset network failure: {e.Message}");
                return false;
            }
        }

        public async Task<PlayerProfileDto> LoginAsync(string username, string password)
        {
            try
            {
                var dto = new LoginRequestDto(username, password);
                string jsonBody = JsonConvert.SerializeObject(dto);
                var content = new StringContent(jsonBody, Encoding.UTF8, "application/json");

                HttpResponseMessage response = await _httpClient.PostAsync($"{ServerBaseUrl}/login", content);
                Debug.Log($"HTTP Status Code: {(int)response.StatusCode}");

                if (response.IsSuccessStatusCode)
                {
                    if (response.Headers.TryGetValues("Authorization", out var values))
                    {
                        foreach (var authHeader in values)
                        {
                            if (authHeader.StartsWith("Bearer ", StringComparison.OrdinalIgnoreCase))
                            {
                                GameContext.JwtToken = authHeader.Substring(7);
                                Debug.Log("[Auth Client] Successfully intercepted and saved JWT Token!");
                            }
                        }
                    }

                    string body = await response.Content.ReadAsStringAsync();
                    return JsonConvert.DeserializeObject<PlayerProfileDto>(body);
                }
            }
            catch (Exception e)
            {
                Debug.LogError($"Login network failure: {e.Message}");
            }
            return null;
        }

        public async Task<bool> RegisterAsync(string username, string email, string password)
        {
            try
            {
                var dto = new RegisterRequestDto(username, email, password);
                string jsonBody = JsonConvert.SerializeObject(dto);
                var content = new StringContent(jsonBody, Encoding.UTF8, "application/json");

                HttpResponseMessage response = await _httpClient.PostAsync($"{ServerBaseUrl}/register", content);
                return response.StatusCode == System.Net.HttpStatusCode.Created;
            }
            catch (Exception e)
            {
                Debug.LogError($"Registration network failure: {e.Message}");
                return false;
            }
        }
    }
}