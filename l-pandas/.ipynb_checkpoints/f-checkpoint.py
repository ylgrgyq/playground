import pandas as pd

df = pd.read_excel('team.xlsx')
df.set_index('name', inplace=True)
df['Q1'].plot()
print(df)