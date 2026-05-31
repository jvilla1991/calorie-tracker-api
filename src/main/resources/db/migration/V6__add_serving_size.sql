-- Add optional serving_size_g column to foods.
-- When set, clients can log food in "servings" rather than raw grams.
-- Nutrition values remain stored per 100g; serving_size_g is the reference weight
-- for one serving, used purely to convert "X servings" → grams on the client.
ALTER TABLE foods ADD COLUMN serving_size_g NUMERIC;
