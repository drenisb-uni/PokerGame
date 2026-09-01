using System;
using System.Collections.Generic;
using System.Net.Http;
using System.Threading.Tasks;
using Core.Models;
using Newtonsoft.Json;
using UnityEngine;

namespace Networking.Authentication
{
public class ProfileNetworkClient
    {
        private readonly HttpClient _httpClient = new();
        private const string ServerBaseUrl = "http://localhost:8080/api/profile";

        public async Task<List<PlayerHandResultDto>> LoadRecentHandsAsync(string playerId, int limit)
        {
            if (string.IsNullOrWhiteSpace(playerId)) return new List<PlayerHandResultDto>();

            try
            {
                string encodedPlayerId = Uri.EscapeDataString(playerId);
                string url = $"{ServerBaseUrl}/{encodedPlayerId}/recent-hands?limit={limit}";

                HttpResponseMessage response = await _httpClient.GetAsync(url);
                if (response.IsSuccessStatusCode)
                {
                    string body = await response.Content.ReadAsStringAsync();
                    return JsonConvert.DeserializeObject<List<PlayerHandResultDto>>(body) ?? new List<PlayerHandResultDto>();
                }
            }
            catch (Exception e)
            {
                Debug.LogError($"Could not load recent hands: {e.Message}");
            }
            return new List<PlayerHandResultDto>();
        }

        public async Task<PlayerProfileDto> LoadProfileAsync(string playerId)
        {
            if (string.IsNullOrWhiteSpace(playerId)) return null;

            try
            {
                string encodedPlayerId = Uri.EscapeDataString(playerId);
                string url = $"{ServerBaseUrl}/{encodedPlayerId}";

                HttpResponseMessage response = await _httpClient.GetAsync(url);
                if (response.IsSuccessStatusCode)
                {
                    string body = await response.Content.ReadAsStringAsync();
                    return JsonConvert.DeserializeObject<PlayerProfileDto>(body);
                }
            }
            catch (Exception e)
            {
                Debug.LogError($"Could not load profile: {e.Message}");
            }
            return null;
        }
    }
}